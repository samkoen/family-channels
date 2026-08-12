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


def test_can_watch_false_when_no_minutes_remaining():
    quota = build_quota("c1", date.today(), 60, 60)
    assert quota.minutes_remaining == 0
    assert quota.can_watch is False


def test_can_watch_true_when_minutes_remain():
    quota = build_quota("c1", date.today(), 10, 60)
    assert quota.minutes_remaining == 50
    assert quota.can_watch is True


def test_heartbeat_last_minute_sets_can_watch_false():
    quota = build_quota("c1", date.today(), 59, 60)
    updated = apply_heartbeat(quota, 1)
    assert updated.minutes_used == 60
    assert updated.minutes_remaining == 0
    assert updated.can_watch is False
