from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily"
    secret_key: str = "dev-secret"
    youtube_api_key: str = ""
    session_cookie_name: str = "yt_parent_session"
    child_token_ttl_hours: int = 72
    youtube_cache_ttl_seconds: int = 24 * 60 * 60


@lru_cache
def get_settings() -> Settings:
    return Settings()
