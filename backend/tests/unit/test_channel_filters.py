from app.domain.channel_filters import (
    apply_title_filters,
    normalize_filter,
    title_matches_filters,
)


def test_empty_filters_allow_all():
    assert title_matches_filters("Anything", []) is True
    assert title_matches_filters("Anything", ["", "  "]) is True


def test_or_match_case_insensitive():
    filters = ["Dino", "espace"]
    assert title_matches_filters("Les Dinosaures", filters) is True
    assert title_matches_filters("Mission ESPACE", filters) is True
    assert title_matches_filters("Recette de gâteau", filters) is False


def test_apply_title_filters_or():
    videos = [
        {"title": "Dino show", "video_id": "1"},
        {"title": "Cats", "video_id": "2"},
        {"title": "Espace 1", "video_id": "3"},
    ]
    assert apply_title_filters(videos, []) == videos
    filtered = apply_title_filters(videos, ["dino", "espace"])
    assert [v["video_id"] for v in filtered] == ["1", "3"]


def test_normalize_filter():
    assert normalize_filter("  hello   world ") == "hello world"


def test_normalize_strips_bidi_marks_for_hebrew():
    dirty = "\u200fכראמל\u200e"
    assert normalize_filter(dirty) == "כראמל"
    assert title_matches_filters("כראמל 5 | פרק 1", [dirty]) is True
