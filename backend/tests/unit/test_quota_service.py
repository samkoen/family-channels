import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from app.db import Base, SessionLocal, engine, init_db
from app.domain.watch_day import today
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.repositories.watch_repo import WatchRepository
from app.services.family_service import FamilyService
from app.services.quota_service import QuotaService


def setup_module():
    Base.metadata.drop_all(bind=engine)
    init_db()


def _child(limit: int = 5):
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("1111")
    child = ChildRepository(db).create(family.id, "Emma", limit, "#333")
    return db, child


def test_quota_heartbeat_blocks_when_limit_reached():
    db, child = _child(2)
    service = QuotaService(ChildRepository(db), WatchRepository(db))
    service.heartbeat(child.id, 1)
    service.heartbeat(child.id, 1)
    try:
        service.heartbeat(child.id, 1)
        assert False, "expected PermissionError"
    except PermissionError:
        pass
    quota = service.get_quota(child.id, today())
    assert quota.can_watch is False
    assert quota.minutes_used == 2
    db.close()


def test_watch_time_accumulates_across_sessions_same_day():
    db, child = _child(10)
    service = QuotaService(ChildRepository(db), WatchRepository(db))
    # Session 1
    service.heartbeat(child.id, 3)
    # Session 2 (later the same day)
    service.heartbeat(child.id, 2)
    quota = service.get_quota(child.id)
    assert quota.minutes_used == 5
    assert quota.minutes_remaining == 5
    assert quota.can_watch is True
    db.close()
