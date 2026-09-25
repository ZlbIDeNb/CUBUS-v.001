from datetime import UTC, datetime, timedelta

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .config import Settings, get_settings
from .user_store import UserStore


security = HTTPBearer(auto_error=True)


def create_access_token(
    login: str,
    role: str,
    device_name: str,
    settings: Settings,
    email: str | None = None,
    session_version: int | None = None,
    must_change_password: bool = False,
) -> str:
    now = datetime.now(UTC)
    payload = {
        "sub": login,
        "role": role,
        "device": device_name,
        "iat": now,
        "exp": now + timedelta(minutes=settings.access_token_minutes),
    }
    if email is not None:
        payload["email"] = email
        payload["ver"] = session_version or 1
        payload["must_change_password"] = must_change_password
    return jwt.encode(payload, settings.app_secret.get_secret_value(), algorithm="HS256")


def authenticated_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        payload = jwt.decode(
            credentials.credentials,
            settings.app_secret.get_secret_value(),
            algorithms=["HS256"],
        )
    except jwt.PyJWTError as exc:
        raise HTTPException(status_code=401, detail="Недействительная авторизация") from exc
    email = payload.get("email")
    if email:
        account = UserStore(settings.auth_db_path).get(email)
        if account is None or not account.active:
            raise HTTPException(status_code=401, detail="Учётная запись заблокирована")
        if int(payload.get("ver", 0)) != account.session_version:
            raise HTTPException(status_code=401, detail="Сеанс завершён. Войдите снова")
        payload["must_change_password"] = account.must_change_password
    return payload


def current_user(user: dict = Depends(authenticated_user)) -> dict:
    if user.get("must_change_password"):
        raise HTTPException(status_code=403, detail="Сначала измените временный пароль")
    return user


def current_admin(user: dict = Depends(current_user)) -> dict:
    if str(user.get("role", "")).casefold() not in {"администратор", "admin"}:
        raise HTTPException(status_code=403, detail="Доступ только для администратора")
    if not user.get("email"):
        raise HTTPException(status_code=403, detail="Войдите по почте и паролю")
    return user
