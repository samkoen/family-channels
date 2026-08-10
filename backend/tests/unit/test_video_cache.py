import os
from datetime import datetime, timedelta

os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

from app.db import Base, SessionLocal, engine, init_db
from app.domain.video_cache_key import build_video_cache_key
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.repositories.video_cache_repo import VideoCacheRepository
from app.services.channel_service import ChannelService
from app.services.family_service import FamilyService


class TrackingYouTube:
    def __init__(self):
        self.list_calls = 0
        self.search_calls = 0

    def resolve_channel(self, raw_input: str) -> dict:
        return {
            "youtube_channel_id": "UCabcdefghijklmnopqrstuv",
            "title": "Demo Channel",
            "thumbnail_url": "https://example.com/t.jpg",
        }

    def list_classic_videos(self, youtube_channel_id: str, max_results: int = 25):
        self.list_calls += 1
        return [
            {
                "video_id": "vid1",
                "title": "Hello",
                "thumbnail_url": "https://example.com/v.jpg",
                "duration": "PT5M",
            }
        ]

    def search_classic_videos(
        self,
        youtube_channel_id: str,
        query: str,
        max_results: int = 25,
    ):
        self.search_calls += 1
        return [
            {
                "video_id": "vid2",
                "title": f"Dino {query}",
                "thumbnail_url": "https://example.com/v2.jpg",
                "duration": "PT8M",
            }
        ]

    def get_playable_video(self, video_id: str, youtube_channel_id: str):
        return {
            "video_id": video_id,
            "title": "Dino adventure",
            "duration": "PT5M",
            "channel_id": youtube_channel_id,
        }


def setup_module():
    Base.metadata.drop_all(bind=engine)
    init_db()


def _service(db, yt):
    return ChannelService(
        ChannelRepository(db),
        ChildRepository(db),
        yt,
        VideoCacheRepository(db),
        cache_ttl_seconds=3600,
    )


def test_list_videos_uses_cache_on_second_call():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("7777")
    child = ChildRepository(db).create(family.id, "CacheKid", 60, "#111")
    yt = TrackingYouTube()
    service = _service(db, yt)
    channel = service.add_for_child(child.id, "@Demo")
    first = service.list_videos(child.id, channel.id)
    second = service.list_videos(child.id, channel.id)
    assert first == second
    assert yt.list_calls == 1
    db.close()


def test_filter_change_invalidates_cache():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("8888")
    child = ChildRepository(db).create(family.id, "Noa", 60, "#222")
    yt = TrackingYouTube()
    service = _service(db, yt)
    channel = service.add_for_child(child.id, "@Demo")
    service.list_videos(child.id, channel.id)
    assert yt.list_calls == 1
    service.add_filter(channel.id, "dino")
    service.list_videos(child.id, channel.id)
    assert yt.search_calls == 1
    service.list_videos(child.id, channel.id)
    assert yt.search_calls == 1
    db.close()


def test_empty_results_are_not_cached():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("6666")
    child = ChildRepository(db).create(family.id, "Empty", 60, "#444")

    class EmptyThenData(TrackingYouTube):
        def list_classic_videos(self, youtube_channel_id: str, max_results: int = 25):
            self.list_calls += 1
            if self.list_calls == 1:
                return []
            return [
                {
                    "video_id": "vid1",
                    "title": "Hello",
                    "thumbnail_url": "https://example.com/v.jpg",
                    "duration": "PT5M",
                }
            ]

    yt = EmptyThenData()
    service = _service(db, yt)
    channel = service.add_for_child(child.id, "@Demo")
    assert service.list_videos(child.id, channel.id) == []
    second = service.list_videos(child.id, channel.id)
    assert len(second) == 1
    assert yt.list_calls == 2
    db.close()


def test_expired_cache_is_ignored():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("9999")
    child = ChildRepository(db).create(family.id, "Exp", 60, "#333")
    yt = TrackingYouTube()
    service = _service(db, yt)
    channel = service.add_for_child(child.id, "@Demo")
    key = build_video_cache_key(channel.id, "list", [], "")
    VideoCacheRepository(db).put(
        key,
        channel.id,
        [{"video_id": "stale", "title": "Stale"}],
        ttl_seconds=60,
        now=datetime.utcnow() - timedelta(hours=2),
    )
    videos = service.list_videos(child.id, channel.id)
    assert videos[0]["video_id"] == "vid1"
    assert yt.list_calls == 1
    db.close()
