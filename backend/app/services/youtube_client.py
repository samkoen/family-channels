from __future__ import annotations

import httpx

from app.config import get_settings
from app.domain.channel_parse import extract_channel_query
from app.domain.shorts_filter import filter_classic_videos, is_short_video


class YouTubeClient:
    BASE = "https://www.googleapis.com/youtube/v3"

    def __init__(self, api_key: str | None = None, client: httpx.Client | None = None):
        self.api_key = api_key if api_key is not None else get_settings().youtube_api_key
        self.client = client or httpx.Client(timeout=20.0)

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
        data = self._get(
            "search",
            {
                "part": "snippet",
                "type": "video",
                "channelId": youtube_channel_id,
                "q": q,
                "maxResults": page_size,
                "safeSearch": "strict",
                "order": "relevance",
            },
        )
        video_ids = [
            item["id"]["videoId"]
            for item in data.get("items") or []
            if item.get("id", {}).get("videoId")
        ]
        details = self._video_details(video_ids)
        return filter_classic_videos(details)[:max_results]

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

    def _playlist_video_ids(self, playlist_id: str, max_results: int) -> list[str]:
        data = self._get(
            "playlistItems",
            {
                "part": "contentDetails",
                "playlistId": playlist_id,
                "maxResults": min(max_results, 50),
            },
        )
        return [i["contentDetails"]["videoId"] for i in data.get("items") or []]

    def _video_details(self, video_ids: list[str]) -> list[dict]:
        if not video_ids:
            return []
        data = self._get(
            "videos",
            {
                "part": "snippet,contentDetails",
                "id": ",".join(video_ids),
            },
        )
        result = []
        for item in data.get("items") or []:
            result.append(
                {
                    "video_id": item["id"],
                    "title": item["snippet"]["title"],
                    "thumbnail_url": _thumb(item["snippet"]),
                    "duration": item["contentDetails"]["duration"],
                    "channel_id": item["snippet"].get("channelId", ""),
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
        response.raise_for_status()
        return response.json()


def _thumb(snippet: dict) -> str:
    thumbs = snippet.get("thumbnails") or {}
    for key in ("medium", "high", "default"):
        if key in thumbs:
            return thumbs[key]["url"]
    return ""
