from pathlib import Path
from urllib.parse import urlparse

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.orm import Session

from app.api.deps import channel_service, child_repo, family_service
from app.config import get_settings
from app.db import get_db
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.security import make_parent_token, read_parent_token
from app.services.channel_service import ChannelService
from app.services.family_service import FamilyService
from app.web.i18n import normalize_lang, ui_ctx

router = APIRouter(tags=["parent-web"])
templates = Jinja2Templates(directory=str(Path(__file__).resolve().parent / "templates"))


def _family_id(request: Request) -> str | None:
    settings = get_settings()
    token = request.cookies.get(settings.session_cookie_name)
    return read_parent_token(token or "")


def _owned_channel(
    channel_id: str,
    family_id: str,
    repo: ChannelRepository,
    children: ChildRepository,
):
    channel = repo.get(channel_id)
    if not channel:
        return None, None
    child = children.get(channel.child_id)
    if not child or child.family_id != family_id:
        return None, None
    return channel, child


@router.get("/", response_class=HTMLResponse)
def home(request: Request):
    if _family_id(request):
        return RedirectResponse("/dashboard", status_code=303)
    return templates.TemplateResponse("create.html", ui_ctx(request, error=None))


@router.post("/create")
def create_family(
    request: Request,
    pin: str = Form(...),
    pin_confirm: str = Form(...),
    families: FamilyService = Depends(family_service),
):
    if pin != pin_confirm:
        return templates.TemplateResponse(
            "create.html",
            ui_ctx(request, error="pin_mismatch"),
            status_code=400,
        )
    try:
        family = families.create_family(pin)
    except ValueError:
        return templates.TemplateResponse(
            "create.html",
            ui_ctx(request, error="invalid_pin"),
            status_code=400,
        )
    response = templates.TemplateResponse(
        "created.html",
        ui_ctx(request, code=family.code),
    )
    token = make_parent_token(family.id)
    response.set_cookie(
        get_settings().session_cookie_name,
        token,
        httponly=True,
        samesite="lax",
        max_age=60 * 60 * 24 * 14,
    )
    return response


@router.get("/login", response_class=HTMLResponse)
def login_page(request: Request):
    return templates.TemplateResponse("login.html", ui_ctx(request, error=None))


@router.post("/login")
def login_submit(
    request: Request,
    family_code: str = Form(...),
    pin: str = Form(...),
    families: FamilyService = Depends(family_service),
):
    try:
        family = families.authenticate(family_code, pin)
    except PermissionError:
        return templates.TemplateResponse(
            "login.html",
            ui_ctx(request, error="invalid_credentials"),
            status_code=401,
        )
    response = RedirectResponse("/dashboard", status_code=303)
    response.set_cookie(
        get_settings().session_cookie_name,
        make_parent_token(family.id),
        httponly=True,
        samesite="lax",
        max_age=60 * 60 * 24 * 14,
    )
    return response


@router.post("/logout")
def logout():
    response = RedirectResponse("/login", status_code=303)
    response.delete_cookie(get_settings().session_cookie_name)
    return response


@router.get("/dashboard", response_class=HTMLResponse)
def dashboard(
    request: Request,
    children: ChildRepository = Depends(child_repo),
    db: Session = Depends(get_db),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    family = FamilyRepository(db).get_by_id(family_id)
    kids = children.list_by_family(family_id)
    channel_repo = ChannelRepository(db)
    channels_by_child = {
        kid.id: channel_repo.list_by_child(kid.id) for kid in kids
    }
    filters_by_channel = {
        ch.id: channel_repo.list_filters(ch.id)
        for kid in kids
        for ch in channels_by_child[kid.id]
    }
    return templates.TemplateResponse(
        "dashboard.html",
        ui_ctx(
            request,
            family_code=family.code if family else "",
            children=kids,
            channels_by_child=channels_by_child,
            filters_by_channel=filters_by_channel,
            error=request.query_params.get("error"),
            selected_child=request.query_params.get("child"),
        ),
    )


@router.post("/children")
def add_child(
    request: Request,
    name: str = Form(...),
    daily_limit_minutes: int = Form(60),
    children: ChildRepository = Depends(child_repo),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    children.create(family_id, name, daily_limit_minutes, "#4A5568")
    return RedirectResponse("/dashboard", status_code=303)


@router.post("/children/{child_id}/limit")
def update_limit(
    child_id: str,
    request: Request,
    daily_limit_minutes: int = Form(...),
    children: ChildRepository = Depends(child_repo),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    child = children.get(child_id)
    if not child or child.family_id != family_id:
        return RedirectResponse("/dashboard?error=child", status_code=303)
    children.update_limit(child_id, daily_limit_minutes)
    return RedirectResponse(f"/dashboard?child={child_id}", status_code=303)


@router.post("/children/{child_id}/channels")
def add_channel(
    child_id: str,
    request: Request,
    channel_input: str = Form(...),
    children: ChildRepository = Depends(child_repo),
    channels: ChannelService = Depends(channel_service),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    child = children.get(child_id)
    if not child or child.family_id != family_id:
        return RedirectResponse("/dashboard?error=child", status_code=303)
    try:
        channels.add_for_child(child_id, channel_input)
    except Exception:
        return RedirectResponse(
            f"/dashboard?child={child_id}&error=channel",
            status_code=303,
        )
    return RedirectResponse(f"/dashboard?child={child_id}", status_code=303)


@router.post("/channels/{channel_id}/delete")
def delete_channel(
    channel_id: str,
    request: Request,
    db: Session = Depends(get_db),
    children: ChildRepository = Depends(child_repo),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    repo = ChannelRepository(db)
    channel, child = _owned_channel(channel_id, family_id, repo, children)
    if not channel or not child:
        return RedirectResponse("/dashboard", status_code=303)
    repo.delete(channel_id)
    return RedirectResponse(f"/dashboard?child={child.id}", status_code=303)


@router.get("/channels/{channel_id}/edit", response_class=HTMLResponse)
def edit_channel(
    channel_id: str,
    request: Request,
    db: Session = Depends(get_db),
    children: ChildRepository = Depends(child_repo),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    repo = ChannelRepository(db)
    channel, child = _owned_channel(channel_id, family_id, repo, children)
    if not channel or not child:
        return RedirectResponse("/dashboard", status_code=303)
    return templates.TemplateResponse(
        "channel_edit.html",
        ui_ctx(
            request,
            channel=channel,
            child=child,
            filters=repo.list_filters(channel.id),
            error=request.query_params.get("error"),
            cleared=request.query_params.get("cleared"),
        ),
    )


@router.post("/channels/{channel_id}/refresh-cache")
def refresh_channel_cache(
    channel_id: str,
    request: Request,
    db: Session = Depends(get_db),
    children: ChildRepository = Depends(child_repo),
    channels: ChannelService = Depends(channel_service),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    repo = ChannelRepository(db)
    channel, child = _owned_channel(channel_id, family_id, repo, children)
    if not channel or not child:
        return RedirectResponse("/dashboard", status_code=303)
    channels.invalidate_channel_cache(channel_id)
    return RedirectResponse(
        f"/channels/{channel_id}/edit?cleared=1",
        status_code=303,
    )


@router.post("/channels/{channel_id}/filters")
def add_channel_filter(
    channel_id: str,
    request: Request,
    pattern: str = Form(...),
    db: Session = Depends(get_db),
    children: ChildRepository = Depends(child_repo),
    channels: ChannelService = Depends(channel_service),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    repo = ChannelRepository(db)
    channel, child = _owned_channel(channel_id, family_id, repo, children)
    if not channel or not child:
        return RedirectResponse("/dashboard", status_code=303)
    try:
        channels.add_filter(channel_id, pattern)
    except ValueError:
        return RedirectResponse(
            f"/channels/{channel_id}/edit?error=filter",
            status_code=303,
        )
    return RedirectResponse(f"/channels/{channel_id}/edit", status_code=303)


@router.post("/filters/{filter_id}/delete")
def delete_channel_filter(
    filter_id: str,
    request: Request,
    db: Session = Depends(get_db),
    children: ChildRepository = Depends(child_repo),
    channels: ChannelService = Depends(channel_service),
):
    family_id = _family_id(request)
    if not family_id:
        return RedirectResponse("/login", status_code=303)
    repo = ChannelRepository(db)
    row = repo.get_filter(filter_id)
    if not row:
        return RedirectResponse("/dashboard", status_code=303)
    channel, child = _owned_channel(row.channel_id, family_id, repo, children)
    if not channel or not child:
        return RedirectResponse("/dashboard", status_code=303)
    channels.delete_filter(filter_id)
    return RedirectResponse(f"/channels/{channel.id}/edit", status_code=303)


@router.get("/lang/{code}")
def set_lang(code: str, request: Request):
    lang = normalize_lang(code)
    referer = request.headers.get("referer", "")
    path = urlparse(referer).path if referer else "/"
    target = path if path.startswith("/") else "/"
    response = RedirectResponse(target, status_code=303)
    response.set_cookie("lang", lang, max_age=60 * 60 * 24 * 365)
    return response
