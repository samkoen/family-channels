from datetime import datetime, timedelta

from sqlalchemy.orm import Session

from app.models import VideoCacheRow


class VideoCacheRepository:
    def __init__(self, db: Session):
        self.db = db

    def get_fresh(self, cache_key: str, now: datetime | None = None) -> list[dict] | None:
        moment = now or datetime.utcnow()
        row = (
            self.db.query(VideoCacheRow)
            .filter(
                VideoCacheRow.cache_key == cache_key,
                VideoCacheRow.expires_at > moment,
            )
            .first()
        )
        if not row:
            return None
        payload = row.payload or []
        return list(payload)

    def put(
        self,
        cache_key: str,
        channel_id: str,
        videos: list[dict],
        ttl_seconds: int,
        now: datetime | None = None,
    ) -> None:
        moment = now or datetime.utcnow()
        expires = moment + timedelta(seconds=ttl_seconds)
        row = (
            self.db.query(VideoCacheRow)
            .filter(VideoCacheRow.cache_key == cache_key)
            .first()
        )
        if row:
            row.payload = videos
            row.expires_at = expires
            row.channel_id = channel_id
            row.created_at = moment
        else:
            self.db.add(
                VideoCacheRow(
                    cache_key=cache_key,
                    channel_id=channel_id,
                    payload=videos,
                    expires_at=expires,
                    created_at=moment,
                )
            )
        self.db.commit()

    def delete_by_channel(self, channel_id: str) -> int:
        deleted = (
            self.db.query(VideoCacheRow)
            .filter(VideoCacheRow.channel_id == channel_id)
            .delete(synchronize_session=False)
        )
        self.db.commit()
        return deleted

    def delete_expired(self, now: datetime | None = None) -> int:
        moment = now or datetime.utcnow()
        deleted = (
            self.db.query(VideoCacheRow)
            .filter(VideoCacheRow.expires_at <= moment)
            .delete(synchronize_session=False)
        )
        self.db.commit()
        return deleted
