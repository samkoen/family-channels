"""Fetch YouTube results for KANKIDS caramel filter."""
from __future__ import annotations

import json
import sys
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
load_dotenv(ROOT / ".env")

from app.domain.channel_filters import apply_title_filters, title_matches_filters
from app.domain.shorts_filter import is_short_video
from app.services.youtube_client import YouTubeClient

YT_ID = "UC_fUfLFo31WTFZPxgr1EcLA"
PATTERN = "כראמל"
OUT = ROOT / "scripts" / "_kankids_diag.json"


def main() -> None:
    yt = YouTubeClient()
    variants = [PATTERN, "קרמל", "קראמל", "Caramel", "caramel"]
    report = {"channel": YT_ID, "pattern": PATTERN, "variants": {}}

    for q in variants:
        try:
            raw = yt.search_classic_videos(YT_ID, q, max_results=25)
        except Exception as exc:
            report["variants"][q] = {"error": str(exc)}
            continue
        report["variants"][q] = {
            "count": len(raw),
            "titles": [
                {
                    "title": v.get("title"),
                    "video_id": v.get("video_id"),
                    "duration": v.get("duration"),
                    "match_pattern": title_matches_filters(v.get("title", ""), [PATTERN]),
                    "match_self": title_matches_filters(v.get("title", ""), [q]),
                }
                for v in raw[:15]
            ],
            "after_filter_pattern": len(apply_title_filters(raw, [PATTERN])),
        }

    # Also sample recent uploads titles containing caramel-like
    uploads = yt.list_classic_videos(YT_ID, max_results=50)
    report["recent_sample"] = [
        t
        for t in (v.get("title", "") for v in uploads)
        if any(x in t for x in ("כראמ", "קרמל", "קראמ", "Caramel", "caramel"))
    ][:20]
    report["recent_count"] = len(uploads)

    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
