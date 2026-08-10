"""Calendar day used for daily watch quotas."""

from datetime import date, datetime
from zoneinfo import ZoneInfo

# App audience is primarily Israel / UTC+3.
DEFAULT_TZ = ZoneInfo("Asia/Jerusalem")


def today(tz: ZoneInfo | None = None) -> date:
    return datetime.now(tz or DEFAULT_TZ).date()
