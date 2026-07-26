import re

SHORTS_MAX_SECONDS = 60
DURATION_PATTERN = re.compile(
    r"^PT(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?:(?P<seconds>\d+)S)?$"
)


def iso8601_duration_to_seconds(duration: str) -> int:
    match = DURATION_PATTERN.match(duration)
    if not match:
        return 0
    hours = int(match.group("hours") or 0)
    minutes = int(match.group("minutes") or 0)
    seconds = int(match.group("seconds") or 0)
    return hours * 3600 + minutes * 60 + seconds


def is_short_video(duration_iso: str) -> bool:
    return iso8601_duration_to_seconds(duration_iso) <= SHORTS_MAX_SECONDS


def filter_classic_videos(videos: list[dict]) -> list[dict]:
    return [v for v in videos if not is_short_video(v.get("duration", "PT0S"))]
