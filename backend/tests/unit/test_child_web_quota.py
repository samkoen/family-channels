import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily_test",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from app.db import SessionLocal
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.watch_repo import WatchRepository
from app.services.channel_service import ChannelService
from app.services.quota_service import QuotaService
from app.web.child_routes import CHILD_COOKIE
from tests.conftest import api_client, create_test_child, reset_test_db
from tests.fakes import FakeYouTube


def setup_module():
    reset_test_db()


def _child_with_channel(limit: int, family_code: str):
    db, child, token = create_test_child(limit=limit, family_code=family_code)
    child_id = child.id
    service = ChannelService(
        ChannelRepository(db),
        ChildRepository(db),
        FakeYouTube(),
    )
    channel = service.add_for_child(child_id, "@DemoChannel")
    channel_id = channel.id
    db.close()
    return child_id, token, channel_id


def test_web_videos_json_returns_403_when_quota_exceeded():
    child_id, token, channel_id = _child_with_channel(limit=1, family_code="WEB01")
    db = SessionLocal()
    QuotaService(ChildRepository(db), WatchRepository(db)).heartbeat(child_id, 1)
    db.close()

    with api_client() as client:
        client.cookies.set(CHILD_COOKIE, token)
        response = client.get(f"/watch/channels/{channel_id}/videos.json")
    assert response.status_code == 403
    assert response.json()["error"] == "quota_exceeded"


def test_web_videos_page_redirects_when_quota_exceeded():
    child_id, token, channel_id = _child_with_channel(limit=1, family_code="WEB02")
    db = SessionLocal()
    QuotaService(ChildRepository(db), WatchRepository(db)).heartbeat(child_id, 1)
    db.close()

    with api_client() as client:
        client.cookies.set(CHILD_COOKIE, token)
        response = client.get(
            f"/watch/channels/{channel_id}",
            follow_redirects=False,
        )
    assert response.status_code == 303
    assert response.headers["location"] == "/watch/home"


def test_web_heartbeat_json_returns_can_watch_false_when_exceeded():
    child_id, token, _channel_id = _child_with_channel(limit=1, family_code="WEB03")
    db = SessionLocal()
    QuotaService(ChildRepository(db), WatchRepository(db)).heartbeat(child_id, 1)
    db.close()

    with api_client() as client:
        client.cookies.set(CHILD_COOKIE, token)
        response = client.post(
            "/watch/heartbeat.json",
            json={"minutes": 1},
        )
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is False
    assert data["can_watch"] is False
