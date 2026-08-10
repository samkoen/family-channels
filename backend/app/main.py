from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.api.child import router as child_router
from app.db import init_db
from app.web.child_routes import router as child_web_router
from app.web.routes import router as web_router

app = FastAPI(title="YouTube Family API")
static_dir = Path(__file__).resolve().parent / "web" / "static"
app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")
app.include_router(child_router)
app.include_router(web_router)
app.include_router(child_web_router)


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
