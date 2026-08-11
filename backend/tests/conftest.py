import os

from sqlalchemy.exc import DBAPIError

# Postgres local obligatoire pour les tests d'intégration DB.
os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily",
)
os.environ.setdefault("SECRET_KEY", "test-secret")


def reset_test_db() -> None:
    """Drop/recreate schema tables; tolerate races from concurrent pytest runs."""
    from app.db import Base, engine, init_db

    for _ in range(3):
        try:
            Base.metadata.drop_all(bind=engine, checkfirst=True)
            break
        except DBAPIError:
            continue
    init_db()
