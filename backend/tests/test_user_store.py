from app.user_store import UserStore


def test_user_can_sign_in_with_email_case_insensitively(tmp_path):
    store = UserStore(str(tmp_path / "auth.sqlite3"))
    store.upsert("Admin@Example.RU", "very-strong-password", "admin", "Администратор")

    user = store.authenticate("admin@example.ru", "very-strong-password")

    assert user is not None
    assert user.login == "admin"
    assert user.role == "Администратор"


def test_wrong_password_is_rejected(tmp_path):
    store = UserStore(str(tmp_path / "auth.sqlite3"))
    store.upsert("admin@example.ru", "very-strong-password", "admin", "Администратор")

    assert store.authenticate("admin@example.ru", "incorrect-password") is None


def test_password_is_not_stored_as_plain_text(tmp_path):
    database = tmp_path / "auth.sqlite3"
    store = UserStore(str(database))
    store.upsert("admin@example.ru", "very-strong-password", "admin", "Администратор")

    assert b"very-strong-password" not in database.read_bytes()


def test_admin_creates_user_with_one_time_password(tmp_path):
    store = UserStore(str(tmp_path / "auth.sqlite3"))

    user, temporary_password = store.create_with_temporary_password(
        "worker@example.ru", "worker", "Метролог", "admin@example.ru"
    )

    assert user.must_change_password is True
    assert len(temporary_password) >= 10
    assert store.authenticate("worker@example.ru", temporary_password) is not None


def test_password_change_invalidates_sessions_and_clears_temporary_flag(tmp_path):
    store = UserStore(str(tmp_path / "auth.sqlite3"))
    user, _ = store.create_with_temporary_password(
        "worker@example.ru", "worker", "Метролог", "admin@example.ru"
    )

    changed = store.change_password("worker@example.ru", "new-strong-password")

    assert changed.must_change_password is False
    assert changed.session_version == user.session_version + 1
    assert store.authenticate("worker@example.ru", "new-strong-password") is not None


def test_blocking_user_invalidates_sessions(tmp_path):
    store = UserStore(str(tmp_path / "auth.sqlite3"))
    store.upsert("worker@example.ru", "very-strong-password", "worker", "Метролог")
    before = store.get("worker@example.ru")

    blocked = store.set_active("worker@example.ru", False, "admin@example.ru")

    assert blocked.active is False
    assert blocked.session_version == before.session_version + 1
    assert store.authenticate("worker@example.ru", "very-strong-password") is None


def test_upsert_existing_user_replaces_password_and_invalidates_sessions(tmp_path):
    store = UserStore(str(tmp_path / "auth.sqlite3"))
    original = store.upsert(
        "admin@example.ru", "first-strong-password", "admin", "Администратор"
    )

    updated = store.upsert(
        "admin@example.ru", "second-strong-password", "admin", "Администратор"
    )

    assert updated.session_version == original.session_version + 1
    assert store.authenticate("admin@example.ru", "first-strong-password") is None
    assert store.authenticate("admin@example.ru", "second-strong-password") is not None
