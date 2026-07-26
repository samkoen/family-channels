"""Diagnose which DB is used and list family codes (no PIN hashes printed)."""
import os
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")

print("DATABASE_URL host part:", (os.getenv("DATABASE_URL") or "")[:40], "...")
sqlite_path = ROOT / "ytfamily.db"
print("sqlite file exists:", sqlite_path.exists(), sqlite_path)

from sqlalchemy import create_engine, text

url = os.getenv("DATABASE_URL")
if url:
    eng = create_engine(url)
    with eng.connect() as c:
        try:
            rows = list(c.execute(text("SELECT code, created_at FROM families ORDER BY created_at")))
            print("ACTIVE DB families:", rows)
            kids = list(c.execute(text("SELECT name, family_id FROM children")))
            print("ACTIVE DB children:", kids)
        except Exception as exc:
            print("ACTIVE DB error:", exc)

if sqlite_path.exists():
    eng2 = create_engine(f"sqlite+pysqlite:///{sqlite_path}")
    with eng2.connect() as c:
        try:
            rows = list(c.execute(text("SELECT code, created_at FROM families")))
            print("SQLITE families:", rows)
        except Exception as exc:
            print("SQLITE error:", exc)
