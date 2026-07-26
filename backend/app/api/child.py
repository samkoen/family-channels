from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import channel_service, child_repo, family_service, quota_service, require_child
from app.api.schemas import (
    ChannelOut,
    HeartbeatRequest,
    JoinRequest,
    JoinResponse,
    ChildProfile,
    QuotaOut,
    SessionRequest,
    SessionResponse,
    VideoOut,
)
from app.repositories.child_repo import ChildRepository
from app.security import make_child_token
from app.services.channel_service import ChannelService
from app.services.family_service import FamilyService
from app.services.quota_service import QuotaService

router = APIRouter(prefix="/api/child", tags=["child"])


@router.post("/join", response_model=JoinResponse)
def join_family(
    body: JoinRequest,
    families: FamilyService = Depends(family_service),
    children: ChildRepository = Depends(child_repo),
):
    family = families.repo.get_by_code(body.family_code.strip().upper())
    if not family:
        raise HTTPException(status_code=404, detail="family_not_found")
    profiles = [
        ChildProfile(id=c.id, name=c.name, avatar_color=c.avatar_color)
        for c in children.list_by_family(family.id)
    ]
    return JoinResponse(children=profiles)


@router.post("/session", response_model=SessionResponse)
def create_session(
    body: SessionRequest,
    families: FamilyService = Depends(family_service),
    children: ChildRepository = Depends(child_repo),
):
    family = families.repo.get_by_code(body.family_code.strip().upper())
    if not family:
        raise HTTPException(status_code=404, detail="family_not_found")
    child = children.get(body.child_id)
    if not child or child.family_id != family.id:
        raise HTTPException(status_code=404, detail="child_not_found")
    token = make_child_token(child.id, family.id)
    return SessionResponse(token=token, child_id=child.id, name=child.name)


@router.get("/channels", response_model=list[ChannelOut])
def list_channels(
    child: dict = Depends(require_child),
    channels: ChannelService = Depends(channel_service),
):
    rows = channels.list_for_child(child["child_id"])
    return [
        ChannelOut(
            id=r.id,
            title=r.title,
            thumbnail_url=r.thumbnail_url,
            youtube_channel_id=r.youtube_channel_id,
        )
        for r in rows
    ]


@router.get("/videos", response_model=list[VideoOut])
def list_videos(
    channel_id: str,
    child: dict = Depends(require_child),
    channels: ChannelService = Depends(channel_service),
    q: str = "",
):
    try:
        if q.strip():
            videos = channels.search_videos(child["child_id"], channel_id, q)
        else:
            videos = channels.list_videos(child["child_id"], channel_id)
    except PermissionError:
        raise HTTPException(status_code=403, detail="channel_not_allowed")
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    return [VideoOut(**{k: v[k] for k in ("video_id", "title", "thumbnail_url")}) for v in videos]


@router.get("/quota", response_model=QuotaOut)
def get_quota(
    child: dict = Depends(require_child),
    quotas: QuotaService = Depends(quota_service),
):
    quota = quotas.get_quota(child["child_id"])
    return QuotaOut(
        minutes_remaining=quota.minutes_remaining,
        minutes_used=quota.minutes_used,
        daily_limit_minutes=quota.daily_limit_minutes,
        can_watch=quota.can_watch,
    )


@router.post("/watch/heartbeat", response_model=QuotaOut)
def watch_heartbeat(
    body: HeartbeatRequest,
    child: dict = Depends(require_child),
    quotas: QuotaService = Depends(quota_service),
):
    try:
        quota = quotas.heartbeat(child["child_id"], body.minutes)
    except PermissionError:
        raise HTTPException(status_code=403, detail="quota_exceeded")
    return QuotaOut(
        minutes_remaining=quota.minutes_remaining,
        minutes_used=quota.minutes_used,
        daily_limit_minutes=quota.daily_limit_minutes,
        can_watch=quota.can_watch,
    )
