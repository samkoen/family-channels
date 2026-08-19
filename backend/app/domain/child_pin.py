from app.domain.family_code import is_valid_pin
from app.security import hash_pin, verify_pin


def hash_child_pin(pin: str | None) -> str | None:
    clean = (pin or "").strip()
    if not clean:
        return None
    if not is_valid_pin(clean):
        raise ValueError("invalid_pin")
    return hash_pin(clean)


def check_child_pin(pin_hash: str | None, pin: str | None) -> None:
    """Raise PermissionError if this profile is locked and the PIN is wrong."""
    if not pin_hash:
        return
    if not pin or not verify_pin(pin.strip(), pin_hash):
        raise PermissionError("invalid_child_pin")
