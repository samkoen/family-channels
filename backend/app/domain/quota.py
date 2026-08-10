from datetime import date

from app.domain.models import WatchQuota


def build_quota(
    child_id: str,
    day: date,
    minutes_used: int,
    daily_limit_minutes: int,
) -> WatchQuota:
    used = max(0, minutes_used)
    limit = max(0, daily_limit_minutes)
    return WatchQuota(
        child_id=child_id,
        day=day,
        minutes_used=min(used, limit) if limit else used,
        daily_limit_minutes=limit,
    )


def apply_heartbeat(quota: WatchQuota, minutes: int = 1) -> WatchQuota:
    if minutes < 1:
        raise ValueError("invalid_heartbeat")
    if not quota.can_watch:
        raise PermissionError("quota_exceeded")
    new_used = min(
        quota.daily_limit_minutes,
        quota.minutes_used + minutes,
    )
    return build_quota(
        child_id=quota.child_id,
        day=quota.day,
        minutes_used=new_used,
        daily_limit_minutes=quota.daily_limit_minutes,
    )
