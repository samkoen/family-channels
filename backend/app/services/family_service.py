from app.domain.family_code import is_valid_pin
from app.models import FamilyRow
from app.repositories.family_repo import FamilyRepository
from app.security import verify_pin


class FamilyService:
    def __init__(self, repo: FamilyRepository):
        self.repo = repo

    def create_family(self, pin: str) -> FamilyRow:
        if not is_valid_pin(pin):
            raise ValueError("invalid_pin")
        return self.repo.create(pin)

    def authenticate(self, code: str, pin: str) -> FamilyRow:
        family = self.repo.get_by_code(code.strip().upper())
        if not family or not verify_pin(pin, family.pin_hash):
            raise PermissionError("invalid_credentials")
        return family
