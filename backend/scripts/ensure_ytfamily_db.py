import psycopg

ADMIN = "postgresql://postgres:root@127.0.0.1:5432/postgres"
APP = "postgresql://postgres:root@127.0.0.1:5432/ytfamily"

with psycopg.connect(ADMIN, autocommit=True) as conn:
    with conn.cursor() as cur:
        cur.execute("SELECT 1 FROM pg_database WHERE datname = %s", ("ytfamily",))
        if cur.fetchone() is None:
            cur.execute("CREATE DATABASE ytfamily")
            print("created ytfamily")
        else:
            print("ytfamily exists")

with psycopg.connect(APP) as conn:
    with conn.cursor() as cur:
        cur.execute("SELECT current_database(), current_user")
        print("OK", cur.fetchone())
