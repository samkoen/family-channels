from datetime import datetime, timedelta

from sqlalchemy.orm import Session

from app.domain.video_cache_payload import normalize_video_cache_payload
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
        return normalize_video_cache_payload(row.payload)

    def put(
        self,
        cache_key: str,
        channel_id: str,
        videos: list | dict,
        ttl_seconds: int,
        now: datetime | None = None,
    ) -> None:
        moment = now or datetime.utcnow()
        expires = moment + timedelta(seconds=ttl_seconds)
        payload = normalize_video_cache_payload(videos)
        row = (
            self.db.query(VideoCacheRow)
            .filter(VideoCacheRow.cache_key == cache_key)
            .first()
        )
        if row:
            row.payload = payload
            row.expires_at = expires
            row.channel_id = channel_id
            row.created_at = moment
        else:
            self.db.add(
                VideoCacheRow(
                    cache_key=cache_key,
                    channel_id=channel_id,
                    payload=payload,
                    expires_at=expires,
                    created_at=moment,
                )
            )
        self.db.commit()

    def iter_fresh_payloads(
        self,
        channel_id: str,
        now: datetime | None = None,
    ) -> list[list[dict]]:
        moment = now or datetime.utcnow()
        rows = (
            self.db.query(VideoCacheRow)
            .filter(
                VideoCacheRow.channel_id == channel_id,
                VideoCacheRow.expires_at > moment,
            )
            .all()
        )
        return [normalize_video_cache_payload(row.payload)["videos"] for row in rows]

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
