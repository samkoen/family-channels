from app.web.i18n import normalize_lang, strings_for, text_dir, ui_ctx


def test_normalize_lang_accepts_he():
    assert normalize_lang("he") == "he"
    assert normalize_lang("HE") == "he"
    assert normalize_lang("xx") == "fr"


def test_hebrew_is_rtl():
    assert text_dir("he") == "rtl"
    assert text_dir("fr") == "ltr"
    assert text_dir("en") == "ltr"


def test_hebrew_strings_present():
    he = strings_for("he")
    assert he["sign_in"] == "התחברות"
    assert "{n}" in he["min_left"]


class _Cookies(dict):
    def get(self, key, default=None):
        return super().get(key, default)


class _Request:
    def __init__(self, lang: str):
        self.cookies = _Cookies(lang=lang)


def test_ui_ctx_sets_dir_and_t():
    ctx = ui_ctx(_Request("he"), error=None)
    assert ctx["lang"] == "he"
    assert ctx["dir"] == "rtl"
    assert ctx["t"]["continue"] == "המשך"
    assert ctx["error"] is None
