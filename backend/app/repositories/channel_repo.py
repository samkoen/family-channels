from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.domain.channel_filters import normalize_filter
from app.models import ChannelFilterRow, ChannelRow


class ChannelRepository:
    def __init__(self, db: Session):
        self.db = db

    def list_by_child(self, child_id: str) -> list[ChannelRow]:
        return (
            self.db.query(ChannelRow)
            .filter(ChannelRow.child_id == child_id, ChannelRow.status == "approved")
            .order_by(ChannelRow.title)
            .all()
        )

    def get(self, channel_id: str) -> ChannelRow | None:
        return self.db.get(ChannelRow, channel_id)

    def add(
        self,
        child_id: str,
        youtube_channel_id: str,
        title: str,
        thumbnail_url: str,
    ) -> ChannelRow:
        row = ChannelRow(
            child_id=child_id,
            youtube_channel_id=youtube_channel_id,
            title=title,
            thumbnail_url=thumbnail_url,
            status="approved",
        )
        self.db.add(row)
        self.db.commit()
        self.db.refresh(row)
        return row

    def delete(self, channel_id: str) -> bool:
        row = self.get(channel_id)
        if not row:
            return False
        self.db.delete(row)
        self.db.commit()
        return True

    def filter_patterns(self, channel_id: str) -> list[str]:
        rows = (
            self.db.query(ChannelFilterRow)
            .filter(ChannelFilterRow.channel_id == channel_id)
            .order_by(ChannelFilterRow.pattern)
            .all()
        )
        return [r.pattern for r in rows]

    def list_filters(self, channel_id: str) -> list[ChannelFilterRow]:
        return (
            self.db.query(ChannelFilterRow)
            .filter(ChannelFilterRow.channel_id == channel_id)
            .order_by(ChannelFilterRow.pattern)
            .all()
        )

    def add_filter(self, channel_id: str, raw_pattern: str) -> ChannelFilterRow:
        pattern = normalize_filter(raw_pattern)
        if not pattern:
            raise ValueError("empty_filter")
        if len(pattern) > 120:
            raise ValueError("filter_too_long")
        row = ChannelFilterRow(channel_id=channel_id, pattern=pattern)
        self.db.add(row)
        try:
            self.db.commit()
        except IntegrityError:
            self.db.rollback()
            raise ValueError("filter_duplicate")
        self.db.refresh(row)
        return row

    def get_filter(self, filter_id: str) -> ChannelFilterRow | None:
        return self.db.get(ChannelFilterRow, filter_id)

    def delete_filter(self, filter_id: str) -> str | None:
        row = self.get_filter(filter_id)
        if not row:
            return None
        channel_id = row.channel_id
        self.db.delete(row)
        self.db.commit()
        return channel_id
