import re
import secrets
import string


FAMILY_CODE_ALPHABET = string.ascii_uppercase + string.digits
FAMILY_CODE_LENGTH = 6
PIN_PATTERN = re.compile(r"^\d{4,6}$")
FAMILY_NAME_MIN = 3
FAMILY_NAME_MAX = 120


def generate_family_code() -> str:
    return "".join(secrets.choice(FAMILY_CODE_ALPHABET) for _ in range(FAMILY_CODE_LENGTH))


def is_valid_pin(pin: str) -> bool:
    return bool(PIN_PATTERN.match(pin))


def normalize_family_name(name: str) -> str:
    return " ".join((name or "").strip().split()).casefold()


def is_valid_family_name(name: str) -> bool:
    normalized = normalize_family_name(name)
    return FAMILY_NAME_MIN <= len(normalized) <= FAMILY_NAME_MAX
