import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from app.db import Base, SessionLocal, engine, init_db
from app.repositories.family_repo import FamilyRepository
from app.services.family_service import FamilyService


def setup_module():
    Base.metadata.drop_all(bind=engine)
    init_db()


def test_create_and_authenticate_family():
    db = SessionLocal()
    service = FamilyService(FamilyRepository(db))
    family = service.create_family("4242")
    assert len(family.code) == 6
    authed = service.authenticate(family.code, "4242")
    assert authed.id == family.id
    db.close()


def test_authenticate_rejects_bad_pin():
    db = SessionLocal()
    service = FamilyService(FamilyRepository(db))
    family = service.create_family("9999")
    try:
        service.authenticate(family.code, "0000")
        assert False, "expected PermissionError"
    except PermissionError:
        pass
    db.close()
