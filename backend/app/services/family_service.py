from app.domain.family_code import is_valid_family_name, is_valid_pin, normalize_family_name
from app.models import FamilyRow
from app.repositories.family_repo import FamilyRepository
from app.security import verify_pin


class FamilyService:
    def __init__(self, repo: FamilyRepository):
        self.repo = repo

    def create_family(self, name: str, pin: str | None = None) -> FamilyRow:
        if not is_valid_family_name(name):
            raise ValueError("invalid_family_name")
        if self.repo.get_by_name(name):
            raise ValueError("name_taken")
        clean_pin = (pin or "").strip()
        if clean_pin:
            if not is_valid_pin(clean_pin):
                raise ValueError("invalid_pin")
        else:
            clean_pin = None
        return self.repo.create(name, clean_pin)

    def authenticate(self, login: str, pin: str | None = None) -> FamilyRow:
        raw = (login or "").strip()
        if not raw:
            raise PermissionError("invalid_credentials")
        family = self.repo.get_by_name(raw)
        if not family:
            family = self.repo.get_by_code(raw.upper())
        if not family:
            raise PermissionError("invalid_credentials")
        if family.pin_hash:
            if not pin or not verify_pin(pin, family.pin_hash):
                raise PermissionError("invalid_credentials")
        return family

    def normalize_name(self, name: str) -> str:
        return normalize_family_name(name)
