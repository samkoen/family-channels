from datetime import date

import pytest

from app.domain.quota import apply_heartbeat, build_quota


def test_heartbeat_increments():
    quota = build_quota("c1", date.today(), 10, 60)
    updated = apply_heartbeat(quota, 1)
    assert updated.minutes_used == 11
    assert updated.minutes_remaining == 49


def test_heartbeat_rejects_when_exceeded():
    quota = build_quota("c1", date.today(), 60, 60)
    with pytest.raises(PermissionError):
        apply_heartbeat(quota, 1)
