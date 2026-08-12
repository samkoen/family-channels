import os

from contextlib import contextmanager

from sqlalchemy.exc import DBAPIError

# Never point tests at the local app DB (ytfamily) — drop_all would wipe real families.
os.environ["DATABASE_URL"] = (
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily_test"
)
os.environ.setdefault("SECRET_KEY", "test-secret")
os.environ["YOUTUBE_API_KEY"] = ""


def pytest_configure() -> None:
    # Ensure settings/engine pick the test DB even if imported early.
    from fastapi import Depends
    from sqlalchemy.orm import Session

    from app.config import get_settings
    from app import db as app_db
    from app.api.deps import channel_service
    from app.db import get_db
    from app.main import app
    from app.repositories.channel_repo import ChannelRepository
    from app.repositories.child_repo import ChildRepository
    from app.repositories.video_cache_repo import VideoCacheRepository
    from app.services.channel_service import ChannelService
    from app.services.youtube_client import YouTubeClient
    from tests.fakes import FakeYouTube
    import httpx

    get_settings.cache_clear()
    app_db.engine = app_db._build_engine()
    app_db.SessionLocal.configure(bind=app_db.engine)
    # Skip FastAPI startup init_db() — tests already call reset_test_db().
    app.router.on_startup.clear()

    def _fake_channel_service(db: Session = Depends(get_db)) -> ChannelService:
        return ChannelService(
            ChannelRepository(db),
            ChildRepository(db),
            FakeYouTube(),
            VideoCacheRepository(db),
        )

    app.dependency_overrides[channel_service] = _fake_channel_service

    def _blocked_http(self, method, url, *args, **kwargs):
        url_s = str(url)
        allowed = (
            url_s.startswith("http://testserver")
            or "://127.0.0.1" in url_s
            or "://localhost" in url_s
        )
        if allowed:
            return _orig_http(self, method, url, *args, **kwargs)
        raise RuntimeError(f"unit tests must not use the network: {method} {url}")

    def _blocked_youtube(self, *args, **kwargs):
        raise RuntimeError("unit tests must not call YouTube")

    _orig_http = httpx.Client.request
    httpx.Client.request = _blocked_http  # type: ignore[method-assign]
    YouTubeClient._get = _blocked_youtube  # type: ignore[method-assign]


def reset_test_db() -> None:
    """Drop/recreate schema tables; tolerate races from concurrent pytest runs."""
    from app.config import get_settings
    from app.db import Base, SessionLocal, init_db
    from app import db as app_db
    from sqlalchemy import text

    get_settings.cache_clear()
    app_db.engine = app_db._build_engine()
    SessionLocal.configure(bind=app_db.engine)

    assert "ytfamily_test" in get_settings().database_url, get_settings().database_url

    app_db.engine.dispose()
    for _ in range(3):
        try:
            with app_db.engine.begin() as conn:
                conn.execute(text("SET LOCAL lock_timeout = '3s'"))
                Base.metadata.drop_all(bind=conn, checkfirst=True)
            break
        except DBAPIError:
            continue
    init_db()


@contextmanager
def api_client():
    """HTTP client without FastAPI startup (avoids init_db lock during tests)."""
    from fastapi.testclient import TestClient
    from app.main import app

    with TestClient(app, raise_server_exceptions=True) as client:
        yield client


def create_test_child(limit: int = 60, family_code: str = "TST001"):
    """Create family + child in test DB; returns (db, child, bearer_token)."""
    from app.db import SessionLocal
    from app.repositories.child_repo import ChildRepository
    from app.repositories.family_repo import FamilyRepository
    from app.security import make_child_token
    from app.services.family_service import FamilyService

    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family(
        f"test-{family_code}",
        "1111",
    )
    child = ChildRepository(db).create(family.id, "TestChild", limit, "#111")
    token = make_child_token(child.id, family.id)
    return db, child, token
