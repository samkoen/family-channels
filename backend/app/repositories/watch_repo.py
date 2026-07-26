from datetime import date

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
