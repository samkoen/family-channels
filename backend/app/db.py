from collections.abc import Generator

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import get_settings


class Base(DeclarativeBase):
    pass


def _build_engine():
    settings = get_settings()
    return create_engine(settings.database_url, pool_pre_ping=True)


engine = _build_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    from app import models  # noqa: F401

    Base.metadata.create_all(bind=engine)
    _migrate_families_schema()


def _migrate_families_schema() -> None:
    """Additive migration for existing DBs created before family.name existed."""
    insp = inspect(engine)
    if "families" not in insp.get_table_names():
        return
    columns = {col["name"]: col for col in insp.get_columns("families")}
    dialect = engine.dialect.name
    with engine.begin() as conn:
        if "name" not in columns:
            conn.execute(text("ALTER TABLE families ADD COLUMN name VARCHAR(120)"))
            conn.execute(
                text(
                        "UPDATE families SET name = lower(code) "
                        "WHERE name IS NULL OR trim(name) = ''"
                )
            )
            if dialect == "postgresql":
                conn.execute(text("ALTER TABLE families ALTER COLUMN name SET NOT NULL"))
                conn.execute(
                    text(
                        "CREATE UNIQUE INDEX IF NOT EXISTS ix_families_name "
                        "ON families (name)"
                    )
                )
            else:
                conn.execute(
                    text(
                        "CREATE UNIQUE INDEX IF NOT EXISTS ix_families_name "
                        "ON families (name)"
                    )
                )
        # Refresh column metadata after possible ADD COLUMN.
        insp = inspect(engine)
        columns = {col["name"]: col for col in insp.get_columns("families")}
        pin_col = columns.get("pin_hash")
        if pin_col is not None and not pin_col.get("nullable", True):
            if dialect == "postgresql":
                conn.execute(text("ALTER TABLE families ALTER COLUMN pin_hash DROP NOT NULL"))
