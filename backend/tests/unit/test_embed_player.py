from pathlib import Path

from tests.conftest import api_client


def test_embed_player_is_locked_to_one_video_and_16x9():
    with api_client() as client:
        response = client.get("/embed/abcdefghijk")
    assert response.status_code == 200
    html = response.text
    assert "player-box" in html
    assert "100vh * 16 / 9" in html
    assert "getVideoData" in html
    assert "requestCanPlay" in html
    assert "onCanPlayResult" in html
    assert "id === pendingId" in html
    assert "/static/player_guard.js" in html
    assert "FamilyPlayerGuard.install" in html
    assert "loadVideoById" in html
    assert "iv_load_policy" in html
    assert "https://www.youtube.com/embed/" not in html


def test_embed_player_rejects_invalid_id():
    with api_client() as client:
        response = client.get("/embed/bad")
    assert response.status_code == 400


def test_player_guard_blocks_youtube_exit_and_keeps_related_in_app():
    js = (
        Path(__file__).resolve().parents[2] / "app/web/static/player_guard.js"
    ).read_text(encoding="utf-8")
    assert "fc-yt-logo-block" in js
    assert "allow-top-navigation" not in js
    assert "videosUrl" in js
    assert "youtubeLeaveId" in js


def test_child_player_reverts_related_when_can_play_fails():
    html = (
        Path(__file__).resolve().parents[2] / "app/web/templates/child_player.html"
    ).read_text(encoding="utf-8")
    catch = html.split(".catch", 1)[1]
    assert "revertVideo()" in catch
    assert "videoId = next" not in catch
    assert "/static/player_guard.js" in html
    assert "videos.json" in html
