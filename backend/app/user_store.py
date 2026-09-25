from __future__ import annotations

import hashlib
import hmac
import os
import secrets
import sqlite3
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path


PBKDF2_ITERATIONS = 600_000


def normalize_email(email: str) -> str:
    return email.strip().casefold()


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS
    )
    return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${salt.hex()}${digest.hex()}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, iterations, salt_hex, digest_hex = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        digest = hashlib.pbkdf2_hmac(
            "sha256",
            password.encode("utf-8"),
            bytes.fromhex(salt_hex),
            int(iterations),
        )
        return hmac.compare_digest(digest.hex(), digest_hex)
    except (TypeError, ValueError):
        return False


_DUMMY_PASSWORD_HASH = hash_password("invalid-password-placeholder")


@dataclass(frozen=True)
class AuthUser:
    email: str
    login: str
    role: str
    active: bool = True
    must_change_password: bool = False
    session_version: int = 1
    created_at: str = ""
    last_login_at: str | None = None


class UserStore:
    def __init__(self, database_path: str):
        self.path = Path(database_path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path)
        connection.row_factory = sqlite3.Row
        return connection

    def _initialize(self) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS users (
                    email TEXT PRIMARY KEY,
                    login TEXT NOT NULL,
                    role TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    must_change_password INTEGER NOT NULL DEFAULT 0,
                    session_version INTEGER NOT NULL DEFAULT 1,
                    last_login_at TEXT
                )
                """
            )
            columns = {
                row["name"] for row in connection.execute("PRAGMA table_info(users)").fetchall()
            }
            migrations = {
                "must_change_password": "INTEGER NOT NULL DEFAULT 0",
                "session_version": "INTEGER NOT NULL DEFAULT 1",
                "last_login_at": "TEXT",
            }
            for name, definition in migrations.items():
                if name not in columns:
                    connection.execute(f"ALTER TABLE users ADD COLUMN {name} {definition}")
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS auth_audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    actor_email TEXT NOT NULL,
                    action TEXT NOT NULL,
                    target_email TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """
            )

    @staticmethod
    def _from_row(row: sqlite3.Row) -> AuthUser:
        return AuthUser(
            email=row["email"],
            login=row["login"],
            role=row["role"],
            active=bool(row["active"]),
            must_change_password=bool(row["must_change_password"]),
            session_version=int(row["session_version"]),
            created_at=row["created_at"],
            last_login_at=row["last_login_at"],
        )

    def authenticate(self, email: str, password: str) -> AuthUser | None:
        normalized = normalize_email(email)
        with self._connect() as connection:
            row = connection.execute(
                "SELECT * FROM users WHERE email = ?",
                (normalized,),
            ).fetchone()
        if row is None or not row["active"]:
            # Keep roughly the same password-hashing cost for an unknown address.
            verify_password(password, _DUMMY_PASSWORD_HASH)
            return None
        if not verify_password(password, row["password_hash"]):
            return None
        now = datetime.now(UTC).isoformat()
        with self._connect() as connection:
            connection.execute(
                "UPDATE users SET last_login_at = ? WHERE email = ?", (now, normalized)
            )
        updated = dict(row)
        updated["last_login_at"] = now
        return self._from_row(updated)

    def get(self, email: str) -> AuthUser | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT * FROM users WHERE email = ?", (normalize_email(email),)
            ).fetchone()
        return self._from_row(row) if row is not None else None

    def list_users(self) -> list[AuthUser]:
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM users ORDER BY active DESC, email COLLATE NOCASE"
            ).fetchall()
        return [self._from_row(row) for row in rows]

    def upsert(self, email: str, password: str, login: str, role: str) -> AuthUser:
        normalized = normalize_email(email)
        if "@" not in normalized or normalized.startswith("@") or normalized.endswith("@"):
            raise ValueError("Некорректный адрес электронной почты")
        if len(password) < 10:
            raise ValueError("Пароль должен содержать не менее 10 символов")
        if not login.strip():
            raise ValueError("Логин КБ не может быть пустым")
        now = datetime.now(UTC).isoformat()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO users(
                    email, login, role, password_hash, active, created_at, updated_at,
                    must_change_password, session_version
                )
                VALUES (?, ?, ?, ?, 1, ?, ?, 0, 1)
                ON CONFLICT(email) DO UPDATE SET
                    login = excluded.login,
                    role = excluded.role,
                    password_hash = excluded.password_hash,
                    active = 1,
                    must_change_password = 0,
                    session_version = users.session_version + 1,
                    updated_at = excluded.updated_at
                """,
                (normalized, login.strip(), role.strip(), hash_password(password), now, now),
            )
        return self.get(normalized)  # type: ignore[return-value]

    def create_with_temporary_password(
        self, email: str, login: str, role: str, actor_email: str
    ) -> tuple[AuthUser, str]:
        normalized = normalize_email(email)
        if self.get(normalized) is not None:
            raise ValueError("Пользователь с такой почтой уже существует")
        temporary_password = self._temporary_password()
        self._write_temporary_user(normalized, temporary_password, login, role)
        self._audit(actor_email, "create", normalized)
        return self.get(normalized), temporary_password  # type: ignore[return-value]

    def reset_password(self, email: str, actor_email: str) -> tuple[AuthUser, str]:
        normalized = normalize_email(email)
        if self.get(normalized) is None:
            raise KeyError(normalized)
        temporary_password = self._temporary_password()
        now = datetime.now(UTC).isoformat()
        with self._connect() as connection:
            connection.execute(
                """
                UPDATE users SET password_hash = ?, must_change_password = 1,
                    session_version = session_version + 1, active = 1, updated_at = ?
                WHERE email = ?
                """,
                (hash_password(temporary_password), now, normalized),
            )
        self._audit(actor_email, "reset_password", normalized)
        return self.get(normalized), temporary_password  # type: ignore[return-value]

    def change_password(self, email: str, new_password: str) -> AuthUser:
        if len(new_password) < 10:
            raise ValueError("Пароль должен содержать не менее 10 символов")
        normalized = normalize_email(email)
        now = datetime.now(UTC).isoformat()
        with self._connect() as connection:
            cursor = connection.execute(
                """
                UPDATE users SET password_hash = ?, must_change_password = 0,
                    session_version = session_version + 1, updated_at = ?
                WHERE email = ? AND active = 1
                """,
                (hash_password(new_password), now, normalized),
            )
        if cursor.rowcount != 1:
            raise KeyError(normalized)
        self._audit(normalized, "change_password", normalized)
        return self.get(normalized)  # type: ignore[return-value]

    def set_active(self, email: str, active: bool, actor_email: str) -> AuthUser:
        normalized = normalize_email(email)
        now = datetime.now(UTC).isoformat()
        with self._connect() as connection:
            cursor = connection.execute(
                """
                UPDATE users SET active = ?, session_version = session_version + 1,
                    updated_at = ? WHERE email = ?
                """,
                (int(active), now, normalized),
            )
        if cursor.rowcount != 1:
            raise KeyError(normalized)
        self._audit(actor_email, "activate" if active else "block", normalized)
        return self.get(normalized)  # type: ignore[return-value]

    def _write_temporary_user(
        self, email: str, password: str, login: str, role: str
    ) -> None:
        if "@" not in email or email.startswith("@") or email.endswith("@"):
            raise ValueError("Некорректный адрес электронной почты")
        if not login.strip():
            raise ValueError("Логин КБ не может быть пустым")
        now = datetime.now(UTC).isoformat()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO users(
                    email, login, role, password_hash, active, created_at, updated_at,
                    must_change_password, session_version
                ) VALUES (?, ?, ?, ?, 1, ?, ?, 1, 1)
                """,
                (email, login.strip(), role.strip(), hash_password(password), now, now),
            )

    @staticmethod
    def _temporary_password() -> str:
        alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        return "Cb-" + "".join(secrets.choice(alphabet) for _ in range(12))

    def _audit(self, actor_email: str, action: str, target_email: str) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO auth_audit_log(actor_email, action, target_email, created_at)
                VALUES (?, ?, ?, ?)
                """,
                (
                    normalize_email(actor_email),
                    action,
                    normalize_email(target_email),
                    datetime.now(UTC).isoformat(),
                ),
            )
