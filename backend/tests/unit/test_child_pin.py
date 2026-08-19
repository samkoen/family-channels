import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily_test",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

import pytest

from app.domain.child_pin import check_child_pin, hash_child_pin
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.security import make_parent_token
from tests.conftest import api_client, create_test_child, reset_test_db


def setup_module():
    reset_test_db()


def test_hash_child_pin_empty_is_none():
    assert hash_child_pin(None) is None
    assert hash_child_pin("") is None
    assert hash_child_pin("   ") is None


def test_hash_child_pin_rejects_invalid():
    with pytest.raises(ValueError):
        hash_child_pin("12")
    with pytest.raises(ValueError):
        hash_child_pin("abcdef")


def test_unlocked_profile_accepts_any_or_no_pin():
    check_child_pin(None, None)
    check_child_pin(None, "9999")


def test_locked_profile_requires_correct_pin():
    hashed = hash_child_pin("4242")
    check_child_pin(hashed, "4242")
    with pytest.raises(PermissionError):
        check_child_pin(hashed, None)
    with pytest.raises(PermissionError):
        check_child_pin(hashed, "0000")


def _pin_child(family_code: str, pin: str | None):
    db, child, _token = create_test_child(limit=30, family_code=family_code)
    if pin:
        ChildRepository(db).update_pin(child.id, pin)
        db.refresh(child)
    family = FamilyRepository(db).get_by_id(child.family_id)
    payload = (db, child.id, family.id, family.code)
    db.close()
    return payload


def test_join_exposes_has_pin():
    _db, child_id, _family_id, code = _pin_child("PINJ1", "4242")
    with api_client() as client:
        response = client.post("/api/child/join", json={"family_code": code})
    assert response.status_code == 200
    kids = response.json()["children"]
    assert kids[0]["id"] == child_id
    assert kids[0]["has_pin"] is True


def test_session_without_pin_ok_when_unlocked():
    _db, child_id, _family_id, code = _pin_child("PINU1", None)
    with api_client() as client:
        response = client.post(
            "/api/child/session",
            json={"family_code": code, "child_id": child_id},
        )
    assert response.status_code == 200
    assert response.json()["child_id"] == child_id


def test_session_requires_child_pin_when_set():
    _db, child_id, _family_id, code = _pin_child("PINL1", "4242")
    with api_client() as client:
        missing = client.post(
            "/api/child/session",
            json={"family_code": code, "child_id": child_id},
        )
        wrong = client.post(
            "/api/child/session",
            json={"family_code": code, "child_id": child_id, "pin": "0000"},
        )
        ok = client.post(
            "/api/child/session",
            json={"family_code": code, "child_id": child_id, "pin": "4242"},
        )
    assert missing.status_code == 403
    assert missing.json()["detail"] == "invalid_child_pin"
    assert wrong.status_code == 403
    assert ok.status_code == 200


def test_parent_watch_as_skips_child_pin():
    _db, child_id, family_id, _code = _pin_child("PINW1", "4242")
    with api_client() as client:
        client.cookies.set("yt_parent_session", make_parent_token(family_id))
        response = client.post(
            f"/children/{child_id}/watch-as",
            follow_redirects=False,
        )
    assert response.status_code == 303
    assert response.headers["location"] == "/watch/home"
    assert response.cookies.get("yt_child_session")


def test_watch_as_requires_parent_session():
    _db, child_id, _family_id, _code = _pin_child("PINW2", "4242")
    with api_client() as client:
        response = client.post(
            f"/children/{child_id}/watch-as",
            follow_redirects=False,
        )
    assert response.status_code == 303
    assert response.headers["location"] == "/login"
