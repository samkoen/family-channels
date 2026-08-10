"""Channel title filters: empty = allow all; otherwise OR match."""

import re
import unicodedata

# BOM, bidi marks, zero-width chars often sneak in with Hebrew copy/paste.
_INVISIBLE = re.compile(
    r"[\u200b-\u200f\u202a-\u202e\u2060\ufeff\u00ad]"
)


def normalize_filter(raw: str) -> str:
    text = unicodedata.normalize("NFC", raw or "")
    text = _INVISIBLE.sub("", text)
    return " ".join(text.strip().split())


def title_matches_filters(title: str, filters: list[str]) -> bool:
    patterns = [normalize_filter(f) for f in filters if normalize_filter(f)]
    if not patterns:
        return True
    haystack = normalize_filter(title).casefold()
    return any(pattern.casefold() in haystack for pattern in patterns)


def apply_title_filters(videos: list[dict], filters: list[str]) -> list[dict]:
    patterns = [normalize_filter(f) for f in filters if normalize_filter(f)]
    if not patterns:
        return list(videos)
    return [v for v in videos if title_matches_filters(v.get("title", ""), patterns)]


def merge_unique_videos(*batches: list[dict]) -> list[dict]:
    seen: set[str] = set()
    merged: list[dict] = []
    for batch in batches:
        for video in batch:
            video_id = video.get("video_id")
            if not video_id or video_id in seen:
                continue
            seen.add(video_id)
            merged.append(video)
    return merged
