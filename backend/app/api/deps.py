from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.db import get_db
from app.repositories.channel_repo import ChannelRepository
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.repositories.video_cache_repo import VideoCacheRepository
from app.repositories.watch_repo import WatchRepository
from app.security import read_child_token, read_parent_token
from app.services.channel_service import ChannelService
from app.services.family_service import FamilyService
from app.services.quota_service import QuotaService
from app.services.youtube_client import YouTubeClient

bearer = HTTPBearer(auto_error=False)


def family_service(db: Session = Depends(get_db)) -> FamilyService:
    return FamilyService(FamilyRepository(db))


def child_repo(db: Session = Depends(get_db)) -> ChildRepository:
    return ChildRepository(db)


def channel_service(db: Session = Depends(get_db)) -> ChannelService:
    return ChannelService(
        ChannelRepository(db),
        ChildRepository(db),
        YouTubeClient(),
        VideoCacheRepository(db),
    )


def quota_service(db: Session = Depends(get_db)) -> QuotaService:
    return QuotaService(ChildRepository(db), WatchRepository(db))


def require_child(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> dict:
    if not creds:
        raise HTTPException(status_code=401, detail="missing_token")
    data = read_child_token(creds.credentials)
    if not data:
        raise HTTPException(status_code=401, detail="invalid_token")
    return data


def require_parent_family_id(token: str | None) -> str:
    family_id = read_parent_token(token or "")
    if not family_id:
        raise HTTPException(status_code=401, detail="unauthorized")
    return family_id
