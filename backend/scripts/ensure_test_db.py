"""Create local Postgres database ytfamily_test if missing."""

import psycopg

ADMIN = "postgresql://postgres:root@127.0.0.1:5432/postgres"
TEST_DB = "ytfamily_test"


def main() -> None:
    with psycopg.connect(ADMIN, autocommit=True) as conn:
        exists = conn.execute(
            "SELECT 1 FROM pg_database WHERE datname = %s",
            (TEST_DB,),
        ).fetchone()
        if not exists:
            conn.execute(f'CREATE DATABASE "{TEST_DB}" OWNER postgres')
            print(f"created {TEST_DB}")
        else:
            print(f"exists {TEST_DB}")


if __name__ == "__main__":
    main()
