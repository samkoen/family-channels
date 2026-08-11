import re
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from app.api.child import router as child_router
from app.db import init_db
from app.web.child_routes import router as child_web_router
from app.web.routes import router as web_router

app = FastAPI(title="YouTube Family API")
static_dir = Path(__file__).resolve().parent / "web" / "static"
templates = Jinja2Templates(
    directory=str(Path(__file__).resolve().parent / "web" / "templates")
)
app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")
app.include_router(child_router)
app.include_router(web_router)
app.include_router(child_web_router)

_VIDEO_ID_RE = re.compile(r"^[\w-]{6,20}$")


@app.get("/embed/{video_id}", response_class=HTMLResponse)
def embed_player(request: Request, video_id: str) -> HTMLResponse:
    """Public HTTPS page for Android WebView — valid Referer for YouTube (error 153)."""
    if not _VIDEO_ID_RE.fullmatch(video_id):
        raise HTTPException(status_code=400, detail="invalid_video_id")
    return templates.TemplateResponse(
        "embed_player.html",
        {"request": request, "video_id": video_id},
    )


@app.on_event("startup")
def on_startup() -> None:
    init_db()


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/health/youtube")
def health_youtube() -> dict:
    from app.config import get_settings
    from app.services.youtube_client import YouTubeClient

    settings = get_settings()
    if not settings.youtube_api_key:
        return {"ok": False, "error": "youtube_api_key_missing"}
    try:
        return YouTubeClient().ping()
    except Exception as exc:
        return {"ok": False, "error": str(exc)[:240]}
