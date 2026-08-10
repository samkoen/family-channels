from pathlib import Path
import sys
import os
from dotenv import load_dotenv
from sqlalchemy import create_engine, text

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
load_dotenv(ROOT / ".env")
from app.config import get_settings

print("DATABASE_URL:", get_settings().database_url.split("@")[-1])

urls = [get_settings().database_url]
sqlite = ROOT / "ytfamily.db"
if sqlite.exists():
    urls.append(f"sqlite+pysqlite:///{sqlite}")

for url in urls:
    print("\n===", url.split("@")[-1] if "@" in url else url, "===")
    try:
        e = create_engine(url)
        with e.connect() as c:
            fams = list(c.execute(text("SELECT code, id FROM families")))
            print("families:", [(f[0], f[1]) for f in fams])
            chs = list(
                c.execute(
                    text(
                        "SELECT title, youtube_channel_id, child_id FROM channels"
                    )
                )
            )
            for title, yt, child in chs:
                print(" channel:", title.encode("unicode_escape").decode(), yt)
            rows = list(
                c.execute(
                    text(
                        """
                        SELECT f.code, c.name, ch.title, ch.youtube_channel_id
                        FROM channels ch
                        JOIN children c ON c.id = ch.child_id
                        JOIN families f ON f.id = c.family_id
                        WHERE ch.youtube_channel_id = 'UC_fUfLFo31WTFZPxgr1EcLA'
                        """
                    )
                )
            )
            print("kankids matches:", rows)
            # also match by hebrew filter caramel
            filt = list(
                c.execute(
                    text(
                        """
                        SELECT f.code, c.name, cf.pattern, ch.youtube_channel_id
                        FROM channel_filters cf
                        JOIN channels ch ON ch.id = cf.channel_id
                        JOIN children c ON c.id = ch.child_id
                        JOIN families f ON f.id = c.family_id
                        """
                    )
                )
            )
            print("filters:")
            for r in filt:
                print(" ", r[0], r[1].encode("unicode_escape").decode(), r[2].encode("unicode_escape").decode(), r[3])
    except Exception as exc:
        print("error:", type(exc).__name__, exc)
