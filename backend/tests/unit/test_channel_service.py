import os

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily_test",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from app.db import Base, SessionLocal, engine, init_db
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.services.channel_service import ChannelService
from app.services.family_service import FamilyService


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


def setup_module():
    from tests.conftest import reset_test_db

    reset_test_db()


def _service(db):
    return ChannelService(
        ChannelRepository(db),
        ChildRepository(db),
        FakeYouTube(),
    )


def test_add_channel_for_child():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("test-family-2222", "2222")
    child = ChildRepository(db).create(family.id, "Leo", 60, "#444")
    service = _service(db)
    channel = service.add_for_child(child.id, "@DemoChannel")
    assert channel.title == "Demo Channel"
    videos = service.list_videos(child.id, channel.id)
    assert len(videos) == 3
    db.close()


def test_search_videos_in_allowed_channel():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("test-family-3333", "3333")
    child = ChildRepository(db).create(family.id, "Mia", 60, "#555")
    service = _service(db)
    channel = service.add_for_child(child.id, "@DemoChannel")
    videos = service.search_videos(child.id, channel.id, "dino")
    assert videos[0]["video_id"] == "vid2"
    assert service.can_play_video(child.id, channel.id, "vid2") is True
    db.close()


def test_empty_filters_show_all_videos():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("test-family-4444", "4444")
    child = ChildRepository(db).create(family.id, "Sam", 60, "#666")
    service = _service(db)
    channel = service.add_for_child(child.id, "@DemoChannel")
    videos = service.list_videos(child.id, channel.id)
    assert {v["video_id"] for v in videos} == {"vid1", "vid2", "vid3"}
    db.close()


def test_or_filters_limit_videos_and_playback():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("test-family-5555", "5555")
    child = ChildRepository(db).create(family.id, "Noa", 60, "#777")
    service = _service(db)
    channel = service.add_for_child(child.id, "@DemoChannel")
    service.add_filter(channel.id, "dino")
    service.add_filter(channel.id, "space")
    videos = service.list_videos(child.id, channel.id)
    assert {v["video_id"] for v in videos} == {"vid2", "vid3"}
    assert service.can_play_video(child.id, channel.id, "vid1") is False
    assert service.can_play_video(child.id, channel.id, "vid2") is True
    db.close()


def test_filters_scan_whole_channel_not_only_recent_uploads():
    """Filters must scan/search beyond the latest upload page."""

    class TrackingYouTube(FakeYouTube):
        def __init__(self):
            self.search_queries: list[str] = []
            self.scan_calls = 0

        def list_classic_videos(self, youtube_channel_id: str, max_results: int = 25):
            return [
                {
                    "video_id": "recent",
                    "title": "Unrelated recent upload",
                    "thumbnail_url": "https://example.com/r.jpg",
                    "duration": "PT5M",
                }
            ]

        def search_classic_videos(
            self,
            youtube_channel_id: str,
            query: str,
            max_results: int = 25,
        ):
            self.search_queries.append(query)
            return []

        def scan_matching_classic_videos(
            self,
            youtube_channel_id: str,
            title_patterns: list[str],
            max_matches: int = 200,
            max_scan: int = 5000,
        ):
            self.scan_calls += 1
            return [
                {
                    "video_id": f"old-{i}",
                    "title": f"Dino episode {i}",
                    "thumbnail_url": f"https://example.com/{i}.jpg",
                    "duration": "PT8M",
                }
                for i in range(1, 12)
            ]

    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("test-family-6666", "6666")
    child = ChildRepository(db).create(family.id, "Avi", 60, "#888")
    yt = TrackingYouTube()
    service = ChannelService(ChannelRepository(db), ChildRepository(db), yt)
    channel = service.add_for_child(child.id, "@DemoChannel")
    service.add_filter(channel.id, "dino")
    videos = service.list_videos(child.id, channel.id)
    assert yt.search_queries == ["dino"]
    assert yt.scan_calls == 1
    assert len(videos) == 11
    db.close()
