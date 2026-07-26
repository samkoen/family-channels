"""Copy data from local SQLite ytfamily.db into PostgreSQL (replace mode)."""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

import psycopg

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

SQLITE_PATH = ROOT / "ytfamily.db"
PG_URL = "postgresql://postgres:root@127.0.0.1:5432/ytfamily"

# Children first for truncate, parents last; insert order reversed.
TABLES_INSERT = (
    "families",
    "children",
    "channels",
    "channel_filters",
    "watch_days",
    "video_caches",
)
TABLES_TRUNCATE = tuple(reversed(TABLES_INSERT))


def copy_table(sqlite_cur: sqlite3.Cursor, pg_cur: psycopg.Cursor, table: str) -> int:
    sqlite_cur.execute(f'SELECT * FROM "{table}"')
    rows = sqlite_cur.fetchall()
    if not rows:
        return 0
    cols = [d[0] for d in sqlite_cur.description]
    col_list = ", ".join(cols)
    placeholders = ", ".join(["%s"] * len(cols))
    sql = f'INSERT INTO "{table}" ({col_list}) VALUES ({placeholders})'
    pg_cur.executemany(sql, rows)
    return len(rows)


def main() -> None:
    if not SQLITE_PATH.exists():
        raise SystemExit(f"SQLite missing: {SQLITE_PATH}")

    from app.db import init_db

    init_db()

    sqlite_conn = sqlite3.connect(SQLITE_PATH)
    sqlite_cur = sqlite_conn.cursor()
    existing = {
        r[0]
        for r in sqlite_cur.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        ).fetchall()
    }

    with psycopg.connect(PG_URL) as pg_conn:
        with pg_conn.cursor() as pg_cur:
            for table in TABLES_TRUNCATE:
                pg_cur.execute(f'TRUNCATE TABLE "{table}" RESTART IDENTITY CASCADE')
            print("Postgres tables truncated.")

            total = 0
            for table in TABLES_INSERT:
                if table not in existing:
                    print(f"skip {table} (not in sqlite)")
                    continue
                n = copy_table(sqlite_cur, pg_cur, table)
                print(f"{table}: {n} row(s)")
                total += n
            pg_conn.commit()

    sqlite_conn.close()
    print(f"OK - {total} row(s) copied from SQLite to Postgres.")


if __name__ == "__main__":
    main()
