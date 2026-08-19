from pydantic import BaseModel, Field


class JoinRequest(BaseModel):
    family_code: str = Field(min_length=4, max_length=16)


class ChildProfile(BaseModel):
    id: str
    name: str
    avatar_color: str
    has_pin: bool = False


class JoinResponse(BaseModel):
    children: list[ChildProfile]


class SessionRequest(BaseModel):
    family_code: str
    child_id: str
    pin: str | None = None


class SessionResponse(BaseModel):
    token: str
    child_id: str
    name: str


class ChannelOut(BaseModel):
    id: str
    title: str
    thumbnail_url: str
    youtube_channel_id: str


class VideoOut(BaseModel):
    video_id: str
    title: str
    thumbnail_url: str


class QuotaOut(BaseModel):
    minutes_remaining: int
    minutes_used: int
    daily_limit_minutes: int
    can_watch: bool


class HeartbeatRequest(BaseModel):
    minutes: int = 1
