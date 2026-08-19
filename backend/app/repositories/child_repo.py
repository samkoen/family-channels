from sqlalchemy.orm import Session

from app.domain.child_pin import hash_child_pin
from app.models import ChildRow


class ChildRepository:
    def __init__(self, db: Session):
        self.db = db

    def list_by_family(self, family_id: str) -> list[ChildRow]:
        return (
            self.db.query(ChildRow)
            .filter(ChildRow.family_id == family_id)
            .order_by(ChildRow.name)
            .all()
        )

    def get(self, child_id: str) -> ChildRow | None:
        return self.db.get(ChildRow, child_id)

    def create(
        self,
        family_id: str,
        name: str,
        daily_limit_minutes: int,
        avatar_color: str,
        pin: str | None = None,
    ) -> ChildRow:
        child = ChildRow(
            family_id=family_id,
            name=name.strip(),
            daily_limit_minutes=daily_limit_minutes,
            avatar_color=avatar_color,
            pin_hash=hash_child_pin(pin),
        )
        self.db.add(child)
        self.db.commit()
        self.db.refresh(child)
        return child

    def update_limit(self, child_id: str, daily_limit_minutes: int) -> ChildRow | None:
        child = self.get(child_id)
        if not child:
            return None
        child.daily_limit_minutes = daily_limit_minutes
        self.db.commit()
        self.db.refresh(child)
        return child

    def update_pin(self, child_id: str, pin: str | None) -> ChildRow | None:
        child = self.get(child_id)
        if not child:
            return None
        child.pin_hash = hash_child_pin(pin)
        self.db.commit()
        self.db.refresh(child)
        return child

    def delete(self, child_id: str) -> bool:
        child = self.get(child_id)
        if not child:
            return False
        self.db.delete(child)
        self.db.commit()
        return True
