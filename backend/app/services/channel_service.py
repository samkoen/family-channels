from app.config import get_settings
from app.domain.channel_filters import (
    apply_title_filters,
    merge_unique_videos,
    normalize_filter,
    title_matches_filters,
)
from app.domain.video_cache_key import build_video_cache_key
from app.domain.video_cache_payload import dump_video_cache_payload
from app.models import ChannelRow
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.video_cache_repo import VideoCacheRepository
from app.services.youtube_client import YouTubeClient

PAGE_SIZE = 25
# Max videos returned to the child UI for a filtered channel.
_FILTERED_MATCH_LIMIT = 200
# How many uploads to inspect when applying title filters (whole-channel scan).
_CHANNEL_SCAN_LIMIT = 5000


class ChannelService:
    def __init__(
        self,
        channels: ChannelRepository,
        children: ChildRepository,
        youtube: YouTubeClient | None = None,
        cache: VideoCacheRepository | None = None,
        cache_ttl_seconds: int | None = None,
    ):
        self.channels = channels
        self.children = children
        self.youtube = youtube or YouTubeClient()
        self.cache = cache
        self.cache_ttl_seconds = (
            cache_ttl_seconds
            if cache_ttl_seconds is not None
            else get_settings().youtube_cache_ttl_seconds
        )

    def add_for_child(self, child_id: str, raw_input: str) -> ChannelRow:
        child = self.children.get(child_id)
        if not child:
            raise LookupError("child_not_found")
        info = self.youtube.resolve_channel(raw_input)
        return self.channels.add(
            child_id=child_id,
            youtube_channel_id=info["youtube_channel_id"],
            title=info["title"],
            thumbnail_url=info["thumbnail_url"],
        )

    def list_for_child(self, child_id: str) -> list[ChannelRow]:
        return self.channels.list_by_child(child_id)

    def list_videos(self, child_id: str, channel_row_id: str) -> list[dict]:
        channel = self._allowed_channel(child_id, channel_row_id)
        patterns = self.channels.filter_patterns(channel.id)
        return self._ensure_catalog(channel, patterns, query="", needed=PAGE_SIZE)["videos"]

    def list_video_page(
        self,
        child_id: str,
        channel_row_id: str,
        offset: int = 0,
        limit: int = PAGE_SIZE,
        query: str = "",
    ) -> dict:
        channel = self._allowed_channel(child_id, channel_row_id)
        patterns = self.channels.filter_patterns(channel.id)
        offset = max(0, int(offset))
        limit = min(max(1, int(limit)), PAGE_SIZE)
        entry = self._ensure_catalog(
            channel,
            patterns,
            query=query.strip(),
            needed=offset + limit,
        )
        videos = entry["videos"]
        page = videos[offset : offset + limit]
        has_more = (offset + limit) < len(videos) or not entry["complete"]
        return {"videos": page, "has_more": has_more, "offset": offset}

    def search_videos(
        self,
        child_id: str,
        channel_row_id: str,
        query: str,
    ) -> list[dict]:
        channel = self._allowed_channel(child_id, channel_row_id)
        patterns = self.channels.filter_patterns(channel.id)
        return self._ensure_catalog(channel, patterns, query=query.strip(), needed=PAGE_SIZE)[
            "videos"
        ]

    def add_filter(self, channel_row_id: str, pattern: str):
        row = self.channels.add_filter(channel_row_id, pattern)
        self.invalidate_channel_cache(channel_row_id)
        return row

    def delete_filter(self, filter_id: str) -> str | None:
        channel_id = self.channels.delete_filter(filter_id)
        if channel_id:
            self.invalidate_channel_cache(channel_id)
        return channel_id

    def invalidate_channel_cache(self, channel_id: str) -> None:
        if self.cache:
            self.cache.delete_by_channel(channel_id)

    def _ensure_catalog(
        self,
        channel: ChannelRow,
        patterns: list[str],
        query: str,
        needed: int,
    ) -> dict:
        kind = "search" if query else "list"
        key = build_video_cache_key(channel.id, kind, patterns, query)
        entry = {"videos": [], "next_page_token": None, "complete": False}
        if self.cache:
            cached = self.cache.get_fresh(key)
            if cached is not None:
                entry = cached
                if len(entry["videos"]) >= needed or entry["complete"]:
                    return entry

        clean = [normalize_filter(p) for p in patterns if normalize_filter(p)]
        if query or clean:
            videos = self._fetch_videos(channel.youtube_channel_id, patterns, query)
            entry = dump_video_cache_payload(videos, None, True)
            self._save_catalog(key, channel.id, entry)
            return entry

        while len(entry["videos"]) < needed and not entry["complete"]:
            token = entry.get("next_page_token")
            fetch_size = PAGE_SIZE if token else max(needed, len(entry["videos"]) + PAGE_SIZE)
            batch, next_token, done = self._list_classic_page(
                channel.youtube_channel_id,
                max_results=fetch_size,
                page_token=token,
            )
            merged = merge_unique_videos(entry["videos"], batch)
            no_new = not batch or merged == entry["videos"]
            entry = dump_video_cache_payload(
                merged,
                next_token,
                True if no_new else bool(done or not next_token),
            )
            self._save_catalog(key, channel.id, entry)
            if no_new:
                break
        return entry

    def _save_catalog(self, key: str, channel_id: str, entry: dict) -> None:
        # Never cache empty lists: transient YouTube/API failures would hide
        # real videos for the whole TTL (e.g. Hebrew filter "כראמל").
        if self.cache and entry.get("videos"):
            self.cache.put(key, channel_id, entry, self.cache_ttl_seconds)

    def _list_classic_page(
        self,
        youtube_channel_id: str,
        max_results: int,
        page_token: str | None,
    ) -> tuple[list[dict], str | None, bool]:
        page_fn = getattr(self.youtube, "list_classic_page", None)
        if callable(page_fn):
            return page_fn(
                youtube_channel_id,
                max_results=max_results,
                page_token=page_token,
            )
        videos = self.youtube.list_classic_videos(
            youtube_channel_id,
            max_results=max_results,
        )
        return videos, None, True

    def _fetch_videos(
        self,
        youtube_channel_id: str,
        patterns: list[str],
        query: str,
    ) -> list[dict]:
        clean = [normalize_filter(p) for p in patterns if normalize_filter(p)]
        if not clean and not query:
            return self.youtube.list_classic_videos(youtube_channel_id)
        if not clean:
            return self.youtube.search_classic_videos(youtube_channel_id, query)
        matched = self._videos_for_filter_patterns(youtube_channel_id, clean)
        if query:
            q = query.casefold()
            matched = [
                v
                for v in matched
                if q in normalize_filter(v.get("title", "")).casefold()
            ]
        return matched[:_FILTERED_MATCH_LIMIT]

    def _videos_for_filter_patterns(
        self,
        youtube_channel_id: str,
        patterns: list[str],
    ) -> list[dict]:
        # 1) Fast path: YouTube search indexes the whole channel.
        search_batches = [
            self.youtube.search_classic_videos(
                youtube_channel_id,
                pattern,
                max_results=_FILTERED_MATCH_LIMIT,
            )
            for pattern in patterns
        ]
        from_search = apply_title_filters(
            merge_unique_videos(*search_batches),
            patterns,
        )
        if len(from_search) >= 10:
            return from_search[:_FILTERED_MATCH_LIMIT]
        # 2) Fallback / enrichment: paginate uploads and match titles
        #    (covers older episodes when search is empty/weak, e.g. on Render).
        scanned = self.youtube.scan_matching_classic_videos(
            youtube_channel_id,
            patterns,
            max_matches=_FILTERED_MATCH_LIMIT,
            max_scan=_CHANNEL_SCAN_LIMIT,
        )
        merged = merge_unique_videos(from_search, scanned)
        return apply_title_filters(merged, patterns)[:_FILTERED_MATCH_LIMIT]

    def _allowed_channel(self, child_id: str, channel_row_id: str) -> ChannelRow:
        channel = self.channels.get(channel_row_id)
        if not channel or channel.child_id != child_id:
            raise PermissionError("channel_not_allowed")
        return channel

    def can_play_video(
        self,
        child_id: str,
        channel_row_id: str,
        video_id: str,
    ) -> bool:
        channel = self._allowed_channel(child_id, channel_row_id)
        # A video already shown in this channel's catalog must play.
        # A second live YouTube check often disagrees (embed flag, other
        # channelId on the same uploads, API errors) and used to bounce
        # the child back to the list with no player and no logs.
        if self._video_in_catalog(child_id, channel.id, video_id):
            return True
        video = self.youtube.get_playable_video(video_id, channel.youtube_channel_id)
        if not video:
            return False
        patterns = self.channels.filter_patterns(channel.id)
        return title_matches_filters(video.get("title", ""), patterns)

    def _video_in_catalog(self, child_id: str, channel_row_id: str, video_id: str) -> bool:
        if not video_id:
            return False
        for video in self.list_videos(child_id, channel_row_id):
            if video.get("video_id") == video_id:
                return True
        if not self.cache:
            return False
        for payload in self.cache.iter_fresh_payloads(channel_row_id):
            if any(item.get("video_id") == video_id for item in payload):
                return True
        return False
