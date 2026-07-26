import re
import secrets
import string


FAMILY_CODE_ALPHABET = string.ascii_uppercase + string.digits
FAMILY_CODE_LENGTH = 6
PIN_PATTERN = re.compile(r"^\d{4,6}$")


def generate_family_code() -> str:
    return "".join(secrets.choice(FAMILY_CODE_ALPHABET) for _ in range(FAMILY_CODE_LENGTH))


def is_valid_pin(pin: str) -> bool:
    return bool(PIN_PATTERN.match(pin))
