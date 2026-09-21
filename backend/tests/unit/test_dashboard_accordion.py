from app.config import get_settings
from app.db import SessionLocal
from app.repositories.child_repo import ChildRepository
from app.repositories.family_repo import FamilyRepository
from app.security import make_parent_token
from app.services.family_service import FamilyService
from tests.conftest import api_client, reset_test_db


def setup_module():
    reset_test_db()


def test_dashboard_uses_accordion_per_child():
    db = SessionLocal()
    family = FamilyService(FamilyRepository(db)).create_family("acc-family", "2222")
    kids = ChildRepository(db)
    kids.create(family.id, "Alex", 60, "#111")
    second = kids.create(family.id, "Noa", 45, "#222")
    family_id = family.id
    second_id = second.id
    db.close()

    cookie = get_settings().session_cookie_name
    token = make_parent_token(family_id)
    with api_client() as client:
        client.cookies.set(cookie, token)
        page = client.get("/dashboard")
        selected = client.get(f"/dashboard?child={second_id}")

    assert page.status_code == 200
    html = page.text
    assert html.count("<details") == 2
    assert html.count('class="child-summary"') == 2
    assert html.count('<details class="child-acc" open>') == 1
    assert "Alex" in html
    assert "Noa" in html

    selected_html = selected.text
    block = selected_html.split(f'id="child-{second_id}"', 1)[1].split("</section>", 1)[0]
    assert "child-acc" in block
    assert "open" in block
