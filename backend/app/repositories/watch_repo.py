from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import WatchDayRow


class WatchRepository:
    def __init__(self, db: Session):
        self.db = db

    def get_or_create(self, child_id: str, day: date) -> WatchDayRow:
        row = (
            self.db.query(WatchDayRow)
            .filter(WatchDayRow.child_id == child_id, WatchDayRow.day == day)
            .one_or_none()
        )
        if row:
            return row
        row = WatchDayRow(child_id=child_id, day=day, minutes_used=0)
        self.db.add(row)
        self.db.commit()
        self.db.refresh(row)
        return row

    def save(self, row: WatchDayRow) -> WatchDayRow:
        self.db.add(row)
        self.db.commit()
        self.db.refresh(row)
        return row

    def consume_minutes(
        self,
        child_id: str,
        day: date,
        minutes: int,
        daily_limit_minutes: int,
    ) -> int:
        """Atomically add watch minutes for the day. Raises PermissionError if over limit."""
        if minutes < 1:
            raise ValueError("invalid_minutes")
        self.get_or_create(child_id, day)
        row = (
            self.db.execute(
                select(WatchDayRow)
                .where(WatchDayRow.child_id == child_id, WatchDayRow.day == day)
                .with_for_update()
            )
            .scalar_one()
        )
        if row.minutes_used >= daily_limit_minutes:
            raise PermissionError("quota_exceeded")
        row.minutes_used = min(
            daily_limit_minutes,
            row.minutes_used + minutes,
        )
        self.db.commit()
        self.db.refresh(row)
        return row.minutes_used
