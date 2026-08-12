"""In-memory YouTube double — unit tests must never call the real API."""


class FakeYouTube:
    def resolve_channel(self, raw_input: str) -> dict:
        return {
            "youtube_channel_id": "UCabcdefghijklmnopqrstuv",
            "title": "Demo Channel",
            "thumbnail_url": "https://example.com/t.jpg",
        }

    def list_classic_videos(self, youtube_channel_id: str, max_results: int = 25):
        return [
            {
                "video_id": "vid1",
                "title": "Hello world",
                "thumbnail_url": "https://example.com/v.jpg",
                "duration": "PT5M",
            },
            {
                "video_id": "vid2",
                "title": "Dino adventure",
                "thumbnail_url": "https://example.com/v2.jpg",
                "duration": "PT6M",
            },
            {
                "video_id": "vid3",
                "title": "Space mission",
                "thumbnail_url": "https://example.com/v3.jpg",
                "duration": "PT7M",
            },
        ]

    def search_classic_videos(self, youtube_channel_id: str, query: str, max_results: int = 25):
        return [
            v
            for v in self.list_classic_videos(youtube_channel_id)
            if query.casefold() in v["title"].casefold()
        ][:max_results]

    def scan_matching_classic_videos(
        self,
        youtube_channel_id: str,
        title_patterns: list[str],
        max_matches: int = 200,
        max_scan: int = 5000,
    ):
        from app.domain.channel_filters import title_matches_filters

        matched = []
        for video in self.list_classic_videos(youtube_channel_id, max_results=max_scan):
            if title_matches_filters(video.get("title", ""), title_patterns):
                matched.append(video)
            if len(matched) >= max_matches:
                break
        return matched

    def video_belongs_to_channel(self, video_id: str, youtube_channel_id: str) -> bool:
        return self.get_playable_video(video_id, youtube_channel_id) is not None

    def get_playable_video(self, video_id: str, youtube_channel_id: str) -> dict | None:
        for video in self.list_classic_videos(youtube_channel_id):
            if video["video_id"] == video_id:
                return video
        return None
