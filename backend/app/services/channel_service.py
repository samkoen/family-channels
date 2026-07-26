from app.config import get_settings
from app.domain.channel_filters import (
    apply_title_filters,
    merge_unique_videos,
    normalize_filter,
    title_matches_filters,
)
from app.domain.video_cache_key import build_video_cache_key
from app.models import ChannelRow
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.video_cache_repo import VideoCacheRepository
from app.services.youtube_client import YouTubeClient

_FILTERED_LIMIT = 50


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
        return self._get_or_fetch_videos(channel, patterns, query="")

    def search_videos(
        self,
        child_id: str,
        channel_row_id: str,
        query: str,
    ) -> list[dict]:
        channel = self._allowed_channel(child_id, channel_row_id)
        patterns = self.channels.filter_patterns(channel.id)
        return self._get_or_fetch_videos(channel, patterns, query=query.strip())

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

    def _get_or_fetch_videos(
        self,
        channel: ChannelRow,
        patterns: list[str],
        query: str,
    ) -> list[dict]:
        kind = "search" if query else "list"
        key = build_video_cache_key(channel.id, kind, patterns, query)
        if self.cache:
            cached = self.cache.get_fresh(key)
            if cached is not None:
                return cached
        videos = self._fetch_videos(channel.youtube_channel_id, patterns, query)
        if self.cache:
            self.cache.put(key, channel.id, videos, self.cache_ttl_seconds)
        return videos

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
        if not query:
            return self._videos_for_filter_patterns(youtube_channel_id, clean)
        batches = [
            self.youtube.search_classic_videos(
                youtube_channel_id,
                f"{pattern} {query}",
                max_results=_FILTERED_LIMIT,
            )
            for pattern in clean
        ]
        merged = merge_unique_videos(*batches)
        return apply_title_filters(merged, clean)[:_FILTERED_LIMIT]

    def _videos_for_filter_patterns(
        self,
        youtube_channel_id: str,
        patterns: list[str],
    ) -> list[dict]:
        batches = [
            self.youtube.search_classic_videos(
                youtube_channel_id,
                pattern,
                max_results=_FILTERED_LIMIT,
            )
            for pattern in patterns
        ]
        merged = merge_unique_videos(*batches)
        return apply_title_filters(merged, patterns)[:_FILTERED_LIMIT]

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
        video = self.youtube.get_playable_video(video_id, channel.youtube_channel_id)
        if not video:
            return False
        patterns = self.channels.filter_patterns(channel.id)
        return title_matches_filters(video.get("title", ""), patterns)
