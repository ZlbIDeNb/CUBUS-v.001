from datetime import UTC, datetime, timedelta

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .config import Settings, get_settings


security = HTTPBearer(auto_error=True)


def create_access_token(login: str, role: str, device_name: str, settings: Settings) -> str:
    now = datetime.now(UTC)
    payload = {
        "sub": login,
        "role": role,
        "device": device_name,
        "iat": now,
        "exp": now + timedelta(minutes=settings.access_token_minutes),
    }
    return jwt.encode(payload, settings.app_secret.get_secret_value(), algorithm="HS256")


def current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        return jwt.decode(
            credentials.credentials,
            settings.app_secret.get_secret_value(),
            algorithms=["HS256"],
        )
    except jwt.PyJWTError as exc:
        raise HTTPException(status_code=401, detail="Недействительная авторизация") from exc

