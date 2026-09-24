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

## Локальный запуск Android

1. Запустите backend: `python -m uvicorn app.main:app --host 127.0.0.1 --port 8000`.
2. Откройте папку `android` в Android Studio.
3. Запустите эмулятор и debug-сборку приложения.

По умолчанию debug-сборка обращается к `http://127.0.0.1:8000/`. Для эмулятора
перед запуском приложения выполните `adb reverse tcp:8000 tcp:8000`. Так backend
останется закрыт от локальной сети. Для физического телефона задайте адрес
компьютера через Gradle-свойство `API_BASE_URL` и отдельно настройте безопасный
сетевой доступ.
