# Zilisnik Mobile

Безопасная мобильная версия Telegram-бота Zilisnik.

## Состав

- `backend/` — HTTP API между Android и Client Base;
- `android/` — приложение на Kotlin и Jetpack Compose;
- `docs/` — решения по архитектуре и план переноса функций.

Токен Client Base хранится только в backend. Android-приложение никогда не получает его.

## Текущий инкремент

- проверка состояния API;
- регистрация устройства по одноразовому коду;
- получение списка и карточки заявок;
- закрытие заявки с ожиданием подтверждения Client Base;
- защита от повторного закрытия по `Idempotency-Key`;
- Android-экраны входа, списка и карточки заявки.

## Backend

```bash
cd backend
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt
copy .env.example .env
pytest
uvicorn app.main:app --reload
```

Для первых проверок используйте `APP_ENV=dev` и тестовый Client Base.

