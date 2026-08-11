from app.domain.family_code import (
    generate_family_code,
    is_valid_family_name,
    is_valid_pin,
    normalize_family_name,
)


def test_generate_family_code_length():
    code = generate_family_code()
    assert len(code) == 6
    assert code.isalnum()
    assert code.isupper() or any(c.isdigit() for c in code)


def test_valid_pin_accepts_4_to_6_digits():
    assert is_valid_pin("1234")
    assert is_valid_pin("123456")


def test_valid_pin_rejects_bad_values():
    assert not is_valid_pin("12")
    assert not is_valid_pin("abcdef")
    assert not is_valid_pin("1234567")


def test_normalize_family_name():
    assert normalize_family_name("  Parent@Email.COM ") == "parent@email.com"


def test_valid_family_name():
    assert is_valid_family_name("abc")
    assert not is_valid_family_name("ab")
