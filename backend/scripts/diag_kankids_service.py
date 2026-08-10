from __future__ import annotations

import sys
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
load_dotenv(ROOT / ".env")

from sqlalchemy import create_engine, text

from app.config import get_settings
from app.db import SessionLocal
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.video_cache_repo import VideoCacheRepository
from app.services.channel_service import ChannelService

CHANNEL_ID = "e10cea54-3f0b-44e1-bb13-6ffe7ea69830"


def main() -> None:
    eng = create_engine(get_settings().database_url)
    with eng.connect() as c:
        rows = list(
            c.execute(
                text(
                    """
                    SELECT cache_key, expires_at, payload
                    FROM video_caches
                    WHERE channel_id = :id
                    """
                ),
                {"id": CHANNEL_ID},
            )
        )
    print("cache count", len(rows))
    for key, exp, payload in rows:
        n = len(payload) if isinstance(payload, list) else type(payload).__name__
        print("expires", exp, "n=", n, "key=", key)

    db = SessionLocal()
    repo = ChannelRepository(db)
    ch = repo.get(CHANNEL_ID)
    print("channel", ch.title if ch else None, "child", ch.child_id if ch else None)
    print("filters", repo.filter_patterns(CHANNEL_ID))
    if ch:
        svc = ChannelService(
            repo,
            ChildRepository(db),
            cache=VideoCacheRepository(db),
        )
        vids = svc.list_videos(ch.child_id, ch.id)
        print("list_videos", len(vids))
        for v in vids[:5]:
            print("-", v.get("title"))
    db.close()


if __name__ == "__main__":
    main()
