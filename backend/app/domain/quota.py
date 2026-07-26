from datetime import date

from app.domain.models import WatchQuota


def build_quota(
    child_id: str,
    day: date,
    minutes_used: int,
    daily_limit_minutes: int,
) -> WatchQuota:
    return WatchQuota(
        child_id=child_id,
        day=day,
        minutes_used=minutes_used,
        daily_limit_minutes=daily_limit_minutes,
    )


def apply_heartbeat(quota: WatchQuota, minutes: int = 1) -> WatchQuota:
    if minutes < 1:
        raise ValueError("invalid_heartbeat")
    if not quota.can_watch:
        raise PermissionError("quota_exceeded")
    return build_quota(
        child_id=quota.child_id,
        day=quota.day,
        minutes_used=quota.minutes_used + minutes,
        daily_limit_minutes=quota.daily_limit_minutes,
    )
