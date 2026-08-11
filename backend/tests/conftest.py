import os

from sqlalchemy.exc import DBAPIError

# Never point tests at the local app DB (ytfamily) — drop_all would wipe real families.
os.environ["DATABASE_URL"] = (
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily_test"
)
os.environ.setdefault("SECRET_KEY", "test-secret")


def pytest_configure() -> None:
    # Ensure settings/engine pick the test DB even if imported early.
    from app.config import get_settings
    from app import db as app_db

    get_settings.cache_clear()
    app_db.engine = app_db._build_engine()
    app_db.SessionLocal.configure(bind=app_db.engine)


def reset_test_db() -> None:
    """Drop/recreate schema tables; tolerate races from concurrent pytest runs."""
    from app.config import get_settings
    from app.db import Base, SessionLocal, engine, init_db
    from app import db as app_db

    get_settings.cache_clear()
    app_db.engine = app_db._build_engine()
    SessionLocal.configure(bind=app_db.engine)

    assert "ytfamily_test" in get_settings().database_url, get_settings().database_url

    for _ in range(3):
        try:
            Base.metadata.drop_all(bind=app_db.engine, checkfirst=True)
            break
        except DBAPIError:
            continue
    init_db()
