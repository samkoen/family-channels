import uuid
from datetime import date, datetime

from sqlalchemy import Date, DateTime, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


def _uuid() -> str:
    return str(uuid.uuid4())


class FamilyRow(Base):
    __tablename__ = "families"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    name: Mapped[str] = mapped_column(String(120), unique=True, index=True)
    code: Mapped[str] = mapped_column(String(16), unique=True, index=True)
    pin_hash: Mapped[str | None] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    children: Mapped[list["ChildRow"]] = relationship(back_populates="family")


class ChildRow(Base):
    __tablename__ = "children"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    family_id: Mapped[str] = mapped_column(ForeignKey("families.id"), index=True)
    name: Mapped[str] = mapped_column(String(80))
    daily_limit_minutes: Mapped[int] = mapped_column(Integer, default=60)
    avatar_color: Mapped[str] = mapped_column(String(16), default="#4A5568")
    pin_hash: Mapped[str | None] = mapped_column(String(255), nullable=True)

    family: Mapped[FamilyRow] = relationship(back_populates="children")
    channels: Mapped[list["ChannelRow"]] = relationship(back_populates="child")


class ChannelRow(Base):
    __tablename__ = "channels"
    __table_args__ = (
        UniqueConstraint("child_id", "youtube_channel_id", name="uq_child_channel"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    child_id: Mapped[str] = mapped_column(ForeignKey("children.id"), index=True)
    youtube_channel_id: Mapped[str] = mapped_column(String(64))
    title: Mapped[str] = mapped_column(String(200))
    thumbnail_url: Mapped[str] = mapped_column(String(500), default="")
    status: Mapped[str] = mapped_column(String(32), default="approved")

    child: Mapped[ChildRow] = relationship(back_populates="channels")
    filters: Mapped[list["ChannelFilterRow"]] = relationship(
        back_populates="channel",
        cascade="all, delete-orphan",
        order_by="ChannelFilterRow.pattern",
    )


class ChannelFilterRow(Base):
    __tablename__ = "channel_filters"
    __table_args__ = (
        UniqueConstraint("channel_id", "pattern", name="uq_channel_filter"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    channel_id: Mapped[str] = mapped_column(ForeignKey("channels.id"), index=True)
    pattern: Mapped[str] = mapped_column(String(120))

    channel: Mapped[ChannelRow] = relationship(back_populates="filters")


class WatchDayRow(Base):
    __tablename__ = "watch_days"
    __table_args__ = (
        UniqueConstraint("child_id", "day", name="uq_child_day"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    child_id: Mapped[str] = mapped_column(ForeignKey("children.id"), index=True)
    day: Mapped[date] = mapped_column(Date)
    minutes_used: Mapped[int] = mapped_column(Integer, default=0)


class VideoCacheRow(Base):
    __tablename__ = "video_caches"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    cache_key: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    channel_id: Mapped[str] = mapped_column(
        ForeignKey("channels.id", ondelete="CASCADE"),
        index=True,
    )
    payload: Mapped[list] = mapped_column(JSONB, default=list)
    expires_at: Mapped[datetime] = mapped_column(DateTime, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
