import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily_test",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from app.repositories.child_repo import ChildRepository
from app.repositories.watch_repo import WatchRepository
from app.services.quota_service import QuotaService
from tests.conftest import api_client, create_test_child, reset_test_db


def setup_module():
    reset_test_db()


def test_api_get_quota_reflects_usage():
    db, child, token = create_test_child(limit=10, family_code="API01")
    child_id = child.id
    service = QuotaService(ChildRepository(db), WatchRepository(db))
    service.heartbeat(child_id, 3)
    db.close()

    with api_client() as client:
        response = client.get(
            "/api/child/quota",
            headers={"Authorization": f"Bearer {token}"},
        )
    assert response.status_code == 200
    data = response.json()
    assert data["minutes_used"] == 3
    assert data["minutes_remaining"] == 7
    assert data["can_watch"] is True


def test_api_heartbeat_returns_403_when_quota_exceeded():
    db, _child, token = create_test_child(limit=2, family_code="API02")
    db.close()

    headers = {"Authorization": f"Bearer {token}"}
    with api_client() as client:
        assert (
            client.post(
                "/api/child/watch/heartbeat",
                json={"minutes": 1},
                headers=headers,
            ).status_code
            == 200
        )
        assert (
            client.post(
                "/api/child/watch/heartbeat",
                json={"minutes": 1},
                headers=headers,
            ).status_code
            == 200
        )
        response = client.post(
            "/api/child/watch/heartbeat",
            json={"minutes": 1},
            headers=headers,
        )
    assert response.status_code == 403
    assert response.json()["detail"] == "quota_exceeded"


def test_api_heartbeat_last_minute_sets_can_watch_false():
    db, _child, token = create_test_child(limit=1, family_code="API03")
    db.close()

    with api_client() as client:
        response = client.post(
            "/api/child/watch/heartbeat",
            json={"minutes": 1},
            headers={"Authorization": f"Bearer {token}"},
        )
    assert response.status_code == 200
    data = response.json()
    assert data["can_watch"] is False
    assert data["minutes_remaining"] == 0
    assert data["minutes_used"] == 1
