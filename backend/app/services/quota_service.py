from datetime import date

from app.domain.models import WatchQuota
from app.domain.quota import build_quota
from app.domain.watch_day import today as local_today
from app.repositories.child_repo import ChildRepository
from app.repositories.watch_repo import WatchRepository


class QuotaService:
    def __init__(self, children: ChildRepository, watch: WatchRepository):
        self.children = children
        self.watch = watch

    def get_quota(self, child_id: str, day: date | None = None) -> WatchQuota:
        child = self.children.get(child_id)
        if not child:
            raise LookupError("child_not_found")
        day = day or local_today()
        row = self.watch.get_or_create(child_id, day)
        return build_quota(
            child_id=child_id,
            day=day,
            minutes_used=row.minutes_used,
            daily_limit_minutes=child.daily_limit_minutes,
        )

    def heartbeat(self, child_id: str, minutes: int = 1) -> WatchQuota:
        child = self.children.get(child_id)
        if not child:
            raise LookupError("child_not_found")
        day = local_today()
        used = self.watch.consume_minutes(
            child_id=child_id,
            day=day,
            minutes=minutes,
            daily_limit_minutes=child.daily_limit_minutes,
        )
        return build_quota(
            child_id=child_id,
            day=day,
            minutes_used=used,
            daily_limit_minutes=child.daily_limit_minutes,
        )
