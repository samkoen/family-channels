"""Normalize video cache payloads (legacy list vs paginated entry)."""


def normalize_video_cache_payload(payload) -> dict:
    if isinstance(payload, dict) and "videos" in payload:
        return {
            "videos": list(payload.get("videos") or []),
            "next_page_token": payload.get("next_page_token"),
            "complete": bool(payload.get("complete")),
        }
    if isinstance(payload, list):
        return {
            "videos": list(payload),
            "next_page_token": None,
            "complete": False,
        }
    return {"videos": [], "next_page_token": None, "complete": False}


def dump_video_cache_payload(videos: list[dict], next_page_token: str | None, complete: bool) -> dict:
    return {
        "videos": videos,
        "next_page_token": next_page_token,
        "complete": complete,
    }
