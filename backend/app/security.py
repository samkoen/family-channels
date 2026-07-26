from passlib.context import CryptContext
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer

from app.config import get_settings

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_pin(pin: str) -> str:
    return pwd_context.hash(pin)


def verify_pin(pin: str, pin_hash: str) -> bool:
    return pwd_context.verify(pin, pin_hash)


def _serializer() -> URLSafeTimedSerializer:
    return URLSafeTimedSerializer(get_settings().secret_key, salt="yt-family")


def make_parent_token(family_id: str) -> str:
    return _serializer().dumps({"role": "parent", "family_id": family_id})


def read_parent_token(token: str, max_age: int = 60 * 60 * 24 * 14) -> str | None:
    try:
        data = _serializer().loads(token, max_age=max_age)
    except (BadSignature, SignatureExpired):
        return None
    if data.get("role") != "parent":
        return None
    return data.get("family_id")


def make_child_token(child_id: str, family_id: str) -> str:
    return _serializer().dumps(
        {"role": "child", "child_id": child_id, "family_id": family_id}
    )


def read_child_token(token: str) -> dict | None:
    settings = get_settings()
    max_age = settings.child_token_ttl_hours * 3600
    try:
        data = _serializer().loads(token, max_age=max_age)
    except (BadSignature, SignatureExpired):
        return None
    if data.get("role") != "child":
        return None
    return data
