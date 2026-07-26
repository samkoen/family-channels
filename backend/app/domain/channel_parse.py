import re
from urllib.parse import urlparse


HANDLE_PATTERN = re.compile(r"^@?([\w.-]+)$")
CHANNEL_ID_PATTERN = re.compile(r"^UC[\w-]{22}$")


def extract_channel_query(raw: str) -> str:
    value = raw.strip()
    if not value:
        raise ValueError("empty_channel_input")
    if CHANNEL_ID_PATTERN.match(value):
        return value
    if value.startswith("http://") or value.startswith("https://"):
        return _from_url(value)
    match = HANDLE_PATTERN.match(value)
    if match:
        return f"@{match.group(1).lstrip('@')}"
    raise ValueError("invalid_channel_input")


def _from_url(url: str) -> str:
    path = urlparse(url).path.strip("/")
    parts = [p for p in path.split("/") if p]
    if not parts:
        raise ValueError("invalid_channel_url")
    if parts[0] == "channel" and len(parts) >= 2:
        return parts[1]
    if parts[0] == "c" and len(parts) >= 2:
        return parts[1]
    if parts[0] == "user" and len(parts) >= 2:
        return parts[1]
    if parts[0].startswith("@"):
        return parts[0]
    raise ValueError("invalid_channel_url")
