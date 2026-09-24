from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "dev"
    app_secret: SecretStr = Field(min_length=32)
    client_base_url: str
    client_base_token: SecretStr
    client_base_timeout_seconds: float = 20.0
    dadata_api_key: SecretStr | None = None
    dadata_secret_key: SecretStr | None = None
    registration_codes: str = ""
    access_token_minutes: int = 480

    def parsed_registration_codes(self) -> dict[str, tuple[str, str]]:
        result: dict[str, tuple[str, str]] = {}
        for item in filter(None, (part.strip() for part in self.registration_codes.split(","))):
            code, login, role = item.split(":", 2)
            result[code] = (login, role)
        return result


@lru_cache
def get_settings() -> Settings:
    return Settings()
