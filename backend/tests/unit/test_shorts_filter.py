from app.domain.shorts_filter import filter_classic_videos, is_short_video


def test_short_detected_under_60s():
    assert is_short_video("PT45S") is True
    assert is_short_video("PT1M") is True


def test_classic_video_over_60s():
    assert is_short_video("PT1M1S") is False
    assert is_short_video("PT10M") is False


def test_filter_removes_shorts():
    videos = [
        {"video_id": "a", "duration": "PT30S"},
        {"video_id": "b", "duration": "PT5M"},
    ]
    result = filter_classic_videos(videos)
    assert [v["video_id"] for v in result] == ["b"]
