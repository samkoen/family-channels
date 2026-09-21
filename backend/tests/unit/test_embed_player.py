from tests.conftest import api_client


def test_embed_player_is_locked_to_one_video_and_16x9():
    with api_client() as client:
        response = client.get("/embed/abcdefghijk")
    assert response.status_code == 200
    html = response.text
    assert "player-box" in html
    assert "100vh * 16 / 9" in html
    assert "getVideoData" in html
    assert "Android.onEnded" in html
    assert "iv_load_policy" in html
    assert "https://www.youtube.com/embed/" not in html


def test_embed_player_rejects_invalid_id():
    with api_client() as client:
        response = client.get("/embed/bad")
    assert response.status_code == 400
