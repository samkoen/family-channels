from datetime import date

from app.domain.quota import apply_heartbeat, build_quota
from app.domain.models import WatchQuota
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
        day = day or date.today()
        row = self.watch.get_or_create(child_id, day)
        return build_quota(
            child_id=child_id,
            day=day,
            minutes_used=row.minutes_used,
            daily_limit_minutes=child.daily_limit_minutes,
        )

    def heartbeat(self, child_id: str, minutes: int = 1) -> WatchQuota:
        quota = self.get_quota(child_id)
        updated = apply_heartbeat(quota, minutes)
        day = date.today()
        row = self.watch.get_or_create(child_id, day)
        row.minutes_used = updated.minutes_used
        self.watch.save(row)
        return updated
