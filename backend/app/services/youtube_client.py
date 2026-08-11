from __future__ import annotations

import httpx

from app.config import get_settings
from app.domain.channel_parse import extract_channel_query
from app.domain.shorts_filter import filter_classic_videos, is_short_video


class YouTubeClient:
    BASE = "https://www.googleapis.com/youtube/v3"

    def __init__(self, api_key: str | None = None, client: httpx.Client | None = None):
        self.api_key = api_key if api_key is not None else get_settings().youtube_api_key
        # Channel-wide playlist scans can take longer than a single search call.
        self.client = client or httpx.Client(timeout=60.0)

    def resolve_channel(self, raw_input: str) -> dict:
        query = extract_channel_query(raw_input)
        if query.startswith("UC") and len(query) == 24:
            return self._channel_by_id(query)
        handle = query.lstrip("@")
        return self._channel_by_handle(handle)

    def list_classic_videos(self, youtube_channel_id: str, max_results: int = 25) -> list[dict]:
        uploads = self._uploads_playlist_id(youtube_channel_id)
        if not uploads:
            return []
        video_ids = self._playlist_video_ids(uploads, max_results=max_results * 2)
        details = self._video_details(video_ids)
        return filter_classic_videos(details)[:max_results]

    def search_classic_videos(
        self,
        youtube_channel_id: str,
        query: str,
        max_results: int = 25,
    ) -> list[dict]:
        q = query.strip()
        if not q:
            return self.list_classic_videos(youtube_channel_id, max_results)
        page_size = min(max(max_results, 25), 50)
        # strict can over-filter some kids catalogs from datacenter IPs (Render).
        for safe in ("moderate", "none"):
            params = {
                "part": "snippet",
                "type": "video",
                "channelId": youtube_channel_id,
                "q": q,
                "maxResults": page_size,
                "safeSearch": safe,
                "order": "relevance",
            }
            if _has_hebrew(q):
                params["relevanceLanguage"] = "iw"
            data = self._get("search", params)
            video_ids = [
                item["id"]["videoId"]
                for item in data.get("items") or []
                if item.get("id", {}).get("videoId")
            ]
            if not video_ids:
                continue
            details = self._video_details(video_ids)
            classic = filter_classic_videos(details)[:max_results]
            if classic:
                return classic
        return []

    def _channel_by_id(self, channel_id: str) -> dict:
        data = self._get("channels", {"part": "snippet", "id": channel_id})
        items = data.get("items") or []
        if not items:
            raise LookupError("channel_not_found")
        return self._map_channel(items[0])

    def _channel_by_handle(self, handle: str) -> dict:
        data = self._get("channels", {"part": "snippet", "forHandle": handle})
        items = data.get("items") or []
        if items:
            return self._map_channel(items[0])
        search = self._get(
            "search",
            {"part": "snippet", "type": "channel", "q": handle, "maxResults": 1},
        )
        search_items = search.get("items") or []
        if not search_items:
            raise LookupError("channel_not_found")
        channel_id = search_items[0]["snippet"]["channelId"]
        return self._channel_by_id(channel_id)

    def _uploads_playlist_id(self, channel_id: str) -> str | None:
        data = self._get("channels", {"part": "contentDetails", "id": channel_id})
        items = data.get("items") or []
        if not items:
            return None
        related = items[0]["contentDetails"]["relatedPlaylists"]
        return related.get("uploads")

    def scan_matching_classic_videos(
        self,
        youtube_channel_id: str,
        title_patterns: list[str],
        max_matches: int = 200,
        max_scan: int = 5000,
    ) -> list[dict]:
        """Scan channel uploads; filter by title first, then drop Shorts via details."""
        from app.domain.channel_filters import title_matches_filters

        uploads = self._uploads_playlist_id(youtube_channel_id)
        if not uploads or not title_patterns:
            return []
        candidate_ids: list[str] = []
        scanned = 0
        page_token: str | None = None
        while scanned < max_scan and len(candidate_ids) < max_matches * 2:
            page_size = min(50, max_scan - scanned)
            rows, page_token = self._playlist_items_page(
                uploads,
                page_size=page_size,
                page_token=page_token,
            )
            if not rows:
                break
            scanned += len(rows)
            for video_id, title in rows:
                if title_matches_filters(title, title_patterns):
                    candidate_ids.append(video_id)
                    if len(candidate_ids) >= max_matches * 2:
                        break
            if not page_token:
                break
        if not candidate_ids:
            return []
        details = []
        for i in range(0, len(candidate_ids), 50):
            details.extend(self._video_details(candidate_ids[i : i + 50]))
        return filter_classic_videos(details)[:max_matches]

    def _playlist_video_ids(self, playlist_id: str, max_results: int) -> list[str]:
        ids: list[str] = []
        page_token: str | None = None
        while len(ids) < max_results:
            page_size = min(50, max_results - len(ids))
            rows, page_token = self._playlist_items_page(
                playlist_id,
                page_size=page_size,
                page_token=page_token,
            )
            if not rows:
                break
            ids.extend(video_id for video_id, _title in rows)
            if not page_token:
                break
        return ids

    def _playlist_items_page(
        self,
        playlist_id: str,
        page_size: int = 50,
        page_token: str | None = None,
    ) -> tuple[list[tuple[str, str]], str | None]:
        params: dict = {
            "part": "snippet,contentDetails",
            "playlistId": playlist_id,
            "maxResults": min(max(page_size, 1), 50),
        }
        if page_token:
            params["pageToken"] = page_token
        data = self._get("playlistItems", params)
        rows: list[tuple[str, str]] = []
        for item in data.get("items") or []:
            video_id = (item.get("contentDetails") or {}).get("videoId")
            title = (item.get("snippet") or {}).get("title") or ""
            if video_id:
                rows.append((video_id, title))
        return rows, data.get("nextPageToken")

    def _video_details(self, video_ids: list[str]) -> list[dict]:
        # videos.list accepts at most 50 ids; search can return slightly more.
        clean = [vid for vid in video_ids if vid]
        if not clean:
            return []
        result: list[dict] = []
        for i in range(0, len(clean), 50):
            batch = clean[i : i + 50]
            data = self._get(
                "videos",
                {
                    "part": "snippet,contentDetails,status",
                    "id": ",".join(batch),
                },
            )
            for item in data.get("items") or []:
                status = item.get("status") or {}
                result.append(
                    {
                        "video_id": item["id"],
                        "title": item["snippet"]["title"],
                        "thumbnail_url": _thumb(item["snippet"]),
                        "duration": item["contentDetails"]["duration"],
                        "channel_id": item["snippet"].get("channelId", ""),
                        "embeddable": bool(status.get("embeddable", True)),
                    }
                )
        return result

    def video_belongs_to_channel(self, video_id: str, youtube_channel_id: str) -> bool:
        return self.get_playable_video(video_id, youtube_channel_id) is not None

    def get_playable_video(self, video_id: str, youtube_channel_id: str) -> dict | None:
        details = self._video_details([video_id])
        if not details:
            return None
        video = details[0]
        if video.get("channel_id") != youtube_channel_id:
            return None
        if is_short_video(video.get("duration", "PT0S")):
            return None
        if video.get("embeddable") is False:
            return None
        return video

    def _map_channel(self, item: dict) -> dict:
        snippet = item["snippet"]
        return {
            "youtube_channel_id": item["id"],
            "title": snippet["title"],
            "thumbnail_url": _thumb(snippet),
        }

    def _get(self, path: str, params: dict) -> dict:
        if not self.api_key:
            raise RuntimeError("youtube_api_key_missing")
        params = {**params, "key": self.api_key}
        response = self.client.get(f"{self.BASE}/{path}", params=params)
        if response.is_error:
            detail = _youtube_error_detail(response)
            raise RuntimeError(f"youtube_api_{response.status_code}:{detail}")
        return response.json()

    def ping(self) -> dict:
        """Lightweight check that the API key works from this host."""
        data = self._get(
            "channels",
            {"part": "id", "id": "UC_x5XG1OV2P6uZZ5FSM9Ttw"},
        )
        return {"ok": True, "items": len(data.get("items") or [])}


def _has_hebrew(text: str) -> bool:
    return any("\u0590" <= ch <= "\u05FF" for ch in text)


def _youtube_error_detail(response: httpx.Response) -> str:
    try:
        payload = response.json()
        err = payload.get("error") or {}
        reason = ""
        errors = err.get("errors") or []
        if errors:
            reason = str(errors[0].get("reason") or "")
        message = str(err.get("message") or response.text)[:180]
        return f"{reason}:{message}" if reason else message
    except Exception:
        return (response.text or "")[:180]


def _thumb(snippet: dict) -> str:
    thumbs = snippet.get("thumbnails") or {}
    for key in ("medium", "high", "default"):
        if key in thumbs:
            return thumbs[key]["url"]
    return ""
