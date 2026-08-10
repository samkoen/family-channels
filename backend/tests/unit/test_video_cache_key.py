from app.domain.video_cache_key import build_video_cache_key


def test_cache_key_stable_and_sorted():
    a = build_video_cache_key("c1", "list", ["Space", "dino"], "")
    b = build_video_cache_key("c1", "list", ["dino", "space"], "")
    assert a == b
    assert a.startswith("v2:c1:list:")


def test_search_query_changes_key():
    base = build_video_cache_key("c1", "search", ["dino"], "")
    with_q = build_video_cache_key("c1", "search", ["dino"], "episode")
    assert base != with_q
