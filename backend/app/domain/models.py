from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class Family:
    id: str
    code: str


@dataclass(frozen=True)
class Child:
    id: str
    family_id: str
    name: str
    daily_limit_minutes: int
    avatar_color: str


@dataclass(frozen=True)
class Channel:
    id: str
    child_id: str
    youtube_channel_id: str
    title: str
    thumbnail_url: str
    status: str


@dataclass(frozen=True)
class WatchQuota:
    child_id: str
    day: date
    minutes_used: int
    daily_limit_minutes: int

    @property
    def minutes_remaining(self) -> int:
        return max(0, self.daily_limit_minutes - self.minutes_used)

    @property
    def can_watch(self) -> bool:
        return self.minutes_remaining > 0
