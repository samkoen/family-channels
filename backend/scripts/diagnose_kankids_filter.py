"""Diagnose KANKIDS filter matching for כראמל."""
from __future__ import annotations

import os
import sys
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
load_dotenv(ROOT / ".env")

from sqlalchemy import create_engine, text

from app.config import get_settings
from app.domain.channel_filters import apply_title_filters, title_matches_filters
from app.services.youtube_client import YouTubeClient


def main() -> None:
    settings = get_settings()
    eng = create_engine(settings.database_url)
    with eng.connect() as c:
        channels = list(
            c.execute(
                text(
                    """
                    SELECT c.id, c.title, c.youtube_channel_id
                    FROM channels c
                    WHERE c.title ILIKE :q OR c.youtube_channel_id ILIKE :q
                    """
                ),
                {"q": "%KANKIDS%"},
            )
        )
        print("channels:", channels)
        if not channels:
            channels = list(
                c.execute(
                    text(
                        "SELECT id, title, youtube_channel_id FROM channels ORDER BY title"
                    )
                )
            )
            print("all channels:", channels)
            return

        for ch_id, title, yt_id in channels:
            filters = list(
                c.execute(
                    text(
                        "SELECT id, pattern FROM channel_filters WHERE channel_id = :id"
                    ),
                    {"id": ch_id},
                )
            )
            print(f"\n== {title} ({yt_id}) ==")
            print("filters:", filters)
            cache = list(
                c.execute(
                    text(
                        """
                        SELECT cache_key, expires_at,
                               CASE WHEN payload IS NULL THEN -1
                                    ELSE json_array_length(payload::json)
                               END AS n
                        FROM video_caches
                        WHERE channel_id = :id
                        """
                    ),
                    {"id": ch_id},
                )
            )
            # json_array_length may fail on some payloads; fallback
            if not cache:
                cache = list(
                    c.execute(
                        text(
                            "SELECT cache_key, expires_at FROM video_caches WHERE channel_id = :id"
                        ),
                        {"id": ch_id},
                    )
                )
            print("cache rows:", cache)

            yt = YouTubeClient()
            for _, pattern in filters:
                print(f"\n--- YouTube search q={pattern!r} ---")
                try:
                    raw = yt.search_classic_videos(yt_id, pattern, max_results=25)
                except Exception as exc:
                    print("search error:", type(exc).__name__, exc)
                    continue
                print(f"raw classic results: {len(raw)}")
                for v in raw[:10]:
                    t = v.get("title", "")
                    ok = title_matches_filters(t, [pattern])
                    print(f"  match={ok} | {t}")
                filtered = apply_title_filters(raw, [pattern])
                print(f"after title filter: {len(filtered)}")


if __name__ == "__main__":
    main()
