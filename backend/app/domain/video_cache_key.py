"""Stable cache keys for YouTube video list/search results."""

from app.domain.channel_filters import normalize_filter


def build_video_cache_key(
    channel_id: str,
    kind: str,
    patterns: list[str],
    query: str = "",
) -> str:
    filters_part = "|".join(
        sorted(
            normalize_filter(p).casefold()
            for p in patterns
            if normalize_filter(p)
        )
    )
    q = query.strip().casefold()
    return f"v1:{channel_id}:{kind}:{q}:{filters_part}"
