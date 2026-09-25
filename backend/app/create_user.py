import argparse
import getpass

from .config import get_settings
from .user_store import UserStore


def main() -> None:
    parser = argparse.ArgumentParser(description="Создать или обновить пользователя CUBUS")
    parser.add_argument("--email", required=True)
    parser.add_argument("--login", required=True, help="Логин сотрудника в Клиентской базе")
    parser.add_argument("--role", default="metrolog")
    arguments = parser.parse_args()
    password = getpass.getpass("Новый пароль CUBUS: ")
    confirmation = getpass.getpass("Повторите пароль: ")
    if password != confirmation:
        raise SystemExit("Пароли не совпадают")
    settings = get_settings()
    user = UserStore(settings.auth_db_path).upsert(
        arguments.email, password, arguments.login, arguments.role
    )
    print(f"Пользователь {user.email} сохранён (КБ: {user.login}, роль: {user.role})")


if __name__ == "__main__":
    main()
