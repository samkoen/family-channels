import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily_test",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from app.db import SessionLocal
from app.repositories.family_repo import FamilyRepository
from app.services.family_service import FamilyService
from tests.conftest import reset_test_db


def setup_module():
    reset_test_db()


def test_create_and_authenticate_family():
    db = SessionLocal()
    service = FamilyService(FamilyRepository(db))
    family = service.create_family("parent@example.com", "4242")
    assert len(family.code) == 6
    assert family.name == "parent@example.com"
    authed = service.authenticate("parent@example.com", "4242")
    assert authed.id == family.id
    # Child code still works for parent login too.
    authed_code = service.authenticate(family.code, "4242")
    assert authed_code.id == family.id
    db.close()


def test_create_without_pin_and_login():
    db = SessionLocal()
    service = FamilyService(FamilyRepository(db))
    family = service.create_family("no-pin-family")
    assert family.pin_hash is None
    authed = service.authenticate("no-pin-family")
    assert authed.id == family.id
    db.close()


def test_authenticate_rejects_bad_pin():
    db = SessionLocal()
    service = FamilyService(FamilyRepository(db))
    family = service.create_family("pin-family", "9999")
    try:
        service.authenticate("pin-family", "0000")
        assert False, "expected PermissionError"
    except PermissionError:
        pass
    db.close()


def test_name_taken():
    db = SessionLocal()
    service = FamilyService(FamilyRepository(db))
    service.create_family("Taken Name")
    try:
        service.create_family("taken name")
        assert False, "expected ValueError"
    except ValueError as exc:
        assert str(exc) == "name_taken"
    db.close()
