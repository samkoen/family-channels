from pathlib import Path

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

from app.api.deps import channel_service, child_repo, family_service, quota_service
from app.config import get_settings
from app.domain.child_pin import check_child_pin
from app.repositories.child_repo import ChildRepository
from app.security import make_child_token, read_child_token, read_parent_token
from app.services.channel_service import ChannelService
from app.services.family_service import FamilyService
from app.services.quota_service import QuotaService
from app.web.i18n import ui_ctx

router = APIRouter(tags=["child-web"])
templates = Jinja2Templates(
    directory=str(Path(__file__).resolve().parent / "templates")
)
CHILD_COOKIE = "yt_child_session"
FAMILY_COOKIE = "yt_child_family"


def _child_session(request: Request) -> dict | None:
    token = request.cookies.get(CHILD_COOKIE)
    if not token:
        return None
    return read_child_token(token)


def _clear_child_cookies(response: RedirectResponse) -> RedirectResponse:
    response.delete_cookie(CHILD_COOKIE)
    response.delete_cookie(FAMILY_COOKIE)
    return response


def _parent_owns_family(request: Request, family_id: str) -> bool:
    token = request.cookies.get(get_settings().session_cookie_name)
    return read_parent_token(token or "") == family_id


def _require_child(
    request: Request,
    children: ChildRepository,
) -> tuple[dict, object] | RedirectResponse:
    """Return (session, child) or a redirect that clears a stale session cookie."""
    session = _child_session(request)
    if not session:
        return RedirectResponse("/watch", status_code=303)
    child = children.get(session["child_id"])
    if not child:
        return _clear_child_cookies(RedirectResponse("/watch", status_code=303))
    return session, child


@router.get("/watch", response_class=HTMLResponse)
def child_entry(
    request: Request,
    children: ChildRepository = Depends(child_repo),
):
    session = _child_session(request)
    if session and children.get(session["child_id"]):
        return RedirectResponse("/watch/home", status_code=303)
    if session:
        return _clear_child_cookies(RedirectResponse("/watch", status_code=303))
    return templates.TemplateResponse("child_join.html", ui_ctx(request, error=None))


@router.post("/watch/join")
def child_join(
    request: Request,
    family_code: str = Form(...),
    families: FamilyService = Depends(family_service),
    children: ChildRepository = Depends(child_repo),
):
    code = family_code.strip().upper()
    family = families.repo.get_by_code(code)
    if not family:
        return templates.TemplateResponse(
            "child_join.html",
            ui_ctx(request, error="not_found"),
            status_code=404,
        )
    kids = children.list_by_family(family.id)
    response = templates.TemplateResponse(
        "child_profiles.html",
        ui_ctx(request, children=kids, family_code=code),
    )
    response.set_cookie(FAMILY_COOKIE, code, httponly=True, samesite="lax", max_age=3600)
    return response


@router.post("/watch/select")
def child_select(
    request: Request,
    child_id: str = Form(...),
    pin: str = Form(""),
    families: FamilyService = Depends(family_service),
    children: ChildRepository = Depends(child_repo),
):
    code = request.cookies.get(FAMILY_COOKIE, "")
    family = families.repo.get_by_code(code)
    child = children.get(child_id)
    if not family or not child or child.family_id != family.id:
        return RedirectResponse("/watch", status_code=303)
    try:
        check_child_pin(child.pin_hash, pin)
    except PermissionError:
        kids = children.list_by_family(family.id)
        return templates.TemplateResponse(
            "child_profiles.html",
            ui_ctx(request, children=kids, family_code=code, error="invalid_child_pin"),
            status_code=401,
        )
    token = make_child_token(child.id, family.id)
    response = RedirectResponse("/watch/home", status_code=303)
    response.set_cookie(CHILD_COOKIE, token, httponly=True, samesite="lax", max_age=72 * 3600)
    return response


@router.get("/watch/home", response_class=HTMLResponse)
def child_home(
    request: Request,
    channels: ChannelService = Depends(channel_service),
    quotas: QuotaService = Depends(quota_service),
    children: ChildRepository = Depends(child_repo),
):
    required = _require_child(request, children)
    if isinstance(required, RedirectResponse):
        return required
    session, child = required
    rows = channels.list_for_child(session["child_id"])
    try:
        quota = quotas.get_quota(session["child_id"])
    except LookupError:
        return _clear_child_cookies(RedirectResponse("/watch", status_code=303))
    return templates.TemplateResponse(
        "child_home.html",
        ui_ctx(
            request,
            child_name=child.name,
            channels=rows,
            quota=quota,
            parent_preview=_parent_owns_family(request, session["family_id"]),
        ),
    )


@router.get("/watch/channels/{channel_id}", response_class=HTMLResponse)
def child_videos(
    channel_id: str,
    request: Request,
    q: str = "",
    refresh: int = 0,
    channels: ChannelService = Depends(channel_service),
    quotas: QuotaService = Depends(quota_service),
    children: ChildRepository = Depends(child_repo),
):
    """Shell page only — videos load via /videos.json so the UI can show a waiter."""
    required = _require_child(request, children)
    if isinstance(required, RedirectResponse):
        return required
    session, _child = required
    try:
        quota = quotas.get_quota(session["child_id"])
    except LookupError:
        return _clear_child_cookies(RedirectResponse("/watch", status_code=303))
    if not quota.can_watch:
        return RedirectResponse("/watch/home", status_code=303)
    channel = channels.channels.get(channel_id)
    if not channel or channel.child_id != session["child_id"]:
        return RedirectResponse("/watch/home", status_code=303)
    return templates.TemplateResponse(
        "child_videos.html",
        ui_ctx(
            request,
            channel=channel,
            channel_id=channel_id,
            quota=quota,
            q=q.strip(),
            refresh=1 if refresh else 0,
        ),
    )


@router.get("/watch/channels/{channel_id}/videos.json")
def child_videos_json(
    channel_id: str,
    request: Request,
    q: str = "",
    refresh: int = 0,
    channels: ChannelService = Depends(channel_service),
    quotas: QuotaService = Depends(quota_service),
    children: ChildRepository = Depends(child_repo),
):
    required = _require_child(request, children)
    if isinstance(required, RedirectResponse):
        return JSONResponse({"ok": False, "error": "unauthorized"}, status_code=401)
    session, _child = required
    try:
        quota = quotas.get_quota(session["child_id"])
    except LookupError:
        return JSONResponse({"ok": False, "error": "unauthorized"}, status_code=401)
    if not quota.can_watch:
        return JSONResponse({"ok": False, "error": "quota_exceeded"}, status_code=403)
    query = q.strip()
    if refresh:
        channels.invalidate_channel_cache(channel_id)
    try:
        if query:
            videos = channels.search_videos(session["child_id"], channel_id, query)
        else:
            videos = channels.list_videos(session["child_id"], channel_id)
    except PermissionError:
        return JSONResponse({"ok": False, "error": "forbidden"}, status_code=403)
    except Exception as exc:
        # Surface enough context in server logs without crashing the child UI.
        print(f"videos.json load_failed channel={channel_id}: {exc!r}")
        return JSONResponse({"ok": False, "error": "load_failed"}, status_code=503)
    return JSONResponse(
        {
            "ok": True,
            "videos": [
                {
                    "video_id": v.get("video_id"),
                    "title": v.get("title"),
                    "thumbnail_url": v.get("thumbnail_url"),
                }
                for v in videos
            ],
        }
    )


@router.get("/watch/play/{channel_id}/{video_id}", response_class=HTMLResponse)
def child_play(
    channel_id: str,
    video_id: str,
    request: Request,
    channels: ChannelService = Depends(channel_service),
    quotas: QuotaService = Depends(quota_service),
    children: ChildRepository = Depends(child_repo),
):
    required = _require_child(request, children)
    if isinstance(required, RedirectResponse):
        return required
    session, _child = required
    try:
        quota = quotas.get_quota(session["child_id"])
    except LookupError:
        return _clear_child_cookies(RedirectResponse("/watch", status_code=303))
    if not quota.can_watch:
        return RedirectResponse("/watch/home", status_code=303)
    try:
        allowed = channels.can_play_video(session["child_id"], channel_id, video_id)
    except PermissionError:
        return RedirectResponse("/watch/home", status_code=303)
    except Exception:
        allowed = False
    if not allowed:
        return RedirectResponse(f"/watch/channels/{channel_id}", status_code=303)
    try:
        # Start of this viewing session counts toward the daily total.
        quota = quotas.heartbeat(session["child_id"], 1)
    except (PermissionError, LookupError):
        return RedirectResponse("/watch/home", status_code=303)
    return templates.TemplateResponse(
        "child_player.html",
        ui_ctx(
            request,
            video_id=video_id,
            channel_id=channel_id,
            quota=quota,
        ),
    )


@router.post("/watch/heartbeat")
def child_heartbeat(
    request: Request,
    quotas: QuotaService = Depends(quota_service),
    children: ChildRepository = Depends(child_repo),
):
    required = _require_child(request, children)
    if isinstance(required, RedirectResponse):
        return required
    session, _child = required
    try:
        quotas.heartbeat(session["child_id"], 1)
    except (PermissionError, LookupError):
        return RedirectResponse("/watch/home", status_code=303)
    return RedirectResponse(request.headers.get("referer", "/watch/home"), status_code=303)


@router.post("/watch/heartbeat.json")
async def child_heartbeat_json(
    request: Request,
    quotas: QuotaService = Depends(quota_service),
    children: ChildRepository = Depends(child_repo),
):
    required = _require_child(request, children)
    if isinstance(required, RedirectResponse):
        return JSONResponse({"ok": False, "can_watch": False}, status_code=401)
    session, _child = required
    minutes = 1
    try:
        payload = await request.json()
        if isinstance(payload, dict) and payload.get("minutes") is not None:
            minutes = max(1, min(30, int(payload["minutes"])))
    except Exception:
        minutes = 1
    try:
        quota = quotas.heartbeat(session["child_id"], minutes)
    except LookupError:
        return JSONResponse({"ok": False, "can_watch": False}, status_code=401)
    except PermissionError:
        return JSONResponse(
            {
                "ok": False,
                "can_watch": False,
                "minutes_remaining": 0,
                "minutes_used": 0,
            }
        )
    return JSONResponse(
        {
            "ok": True,
            "can_watch": quota.can_watch,
            "minutes_remaining": quota.minutes_remaining,
            "minutes_used": quota.minutes_used,
            "daily_limit_minutes": quota.daily_limit_minutes,
        }
    )


@router.post("/watch/leave")
def child_leave():
    response = RedirectResponse("/watch", status_code=303)
    response.delete_cookie(CHILD_COOKIE)
    response.delete_cookie(FAMILY_COOKIE)
    return response
