import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from datetime import date

from app.db import Base, SessionLocal, engine, init_db
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.repositories.watch_repo import WatchRepository
from app.services.family_service import FamilyService
from app.services.quota_service import QuotaService


def setup_module():
    Base.metadata.drop_all(bind=engine)
    init_db()


def _child():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("1111")
    child = ChildRepository(db).create(family.id, "Emma", 2, "#333")
    return db, child


def test_quota_heartbeat_blocks_when_limit_reached():
    db, child = _child()
    service = QuotaService(ChildRepository(db), WatchRepository(db))
    service.heartbeat(child.id, 1)
    service.heartbeat(child.id, 1)
    try:
        service.heartbeat(child.id, 1)
        assert False, "expected PermissionError"
    except PermissionError:
        pass
    quota = service.get_quota(child.id, date.today())
    assert quota.can_watch is False
    db.close()
