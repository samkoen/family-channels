from sqlalchemy.orm import Session

from app.domain.family_code import generate_family_code
from app.models import FamilyRow
from app.security import hash_pin


class FamilyRepository:
    def __init__(self, db: Session):
        self.db = db

    def create(self, pin: str) -> FamilyRow:
        family = FamilyRow(code=self._unique_code(), pin_hash=hash_pin(pin))
        self.db.add(family)
        self.db.commit()
        self.db.refresh(family)
        return family

    def get_by_code(self, code: str) -> FamilyRow | None:
        return (
            self.db.query(FamilyRow)
            .filter(FamilyRow.code == code.upper())
            .one_or_none()
        )

    def get_by_id(self, family_id: str) -> FamilyRow | None:
        return self.db.get(FamilyRow, family_id)

    def _unique_code(self) -> str:
        for _ in range(20):
            code = generate_family_code()
            if not self.get_by_code(code):
                return code
        raise RuntimeError("could_not_generate_family_code")
