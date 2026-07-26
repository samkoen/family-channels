import pytest

from app.domain.channel_parse import extract_channel_query


def test_extract_channel_id_direct():
    assert extract_channel_query("UCabcdefghijklmnopqrstuv") == "UCabcdefghijklmnopqrstuv"


def test_extract_from_channel_url():
    url = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"
    assert extract_channel_query(url) == "UCabcdefghijklmnopqrstuv"


def test_extract_handle():
    assert extract_channel_query("@SomeChannel") == "@SomeChannel"


def test_extract_rejects_empty():
    with pytest.raises(ValueError):
        extract_channel_query("   ")
