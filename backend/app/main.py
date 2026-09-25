import asyncio
import hashlib
import logging
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

from fastapi import Depends, FastAPI, Header, HTTPException, status
from fastapi.responses import FileResponse

from .auth import authenticated_user, create_access_token, current_admin, current_user
from .client_base import ClientBaseClient, ClientBaseError, canonical_application_status
from .config import Settings, get_settings
from .dependencies import get_client_base, load_field_mapping
from .document_store import document_store
from .operations_store import operations_store
from .models import (
    ApplicationDetails,
    ApplicationMapPoint,
    ApplicationStatusCount,
    DailyStatistics,
    TodayStatistics,
    ApplicationSummary,
    AppUpdateInfo,
    AddMeterRequest,
    AddNomenclatureRequest,
    CloseApplicationRequest,
    EmployeeProfile,
    AdministrativeExpensesRequest,
    DocumentationContent,
    DocumentationCreate,
    DocumentationItem,
    HomeAddress,
    HomeAddressRequest,
    OperationResult,
    NomenclatureItem,
    MeterCatalogItem,
    MaterialUsageItem,
    PhotoContent,
    PriceListItem,
    PeriodReport,
    ReworkRequest,
    RegisterRequest,
    LoginRequest,
    ChangePasswordRequest,
    AdminUser,
    AdminUserActiveRequest,
    AdminUserCreateRequest,
    AdminUserCreated,
    AdminEmployeeWorkspace,
    ActivityLogItem,
    RegistryAddress,
    RegistryStats,
    ScheduleDay,
    TokenResponse,
    UploadPhotoRequest,
    WarehouseItem,
    WeatherSnapshot,
    WaterMeter,
)
from .user_store import UserStore


app = FastAPI(title="Zilisnik Mobile API", version="0.1.0")
completed_operations: dict[str, OperationResult] = {}
statistics_scheduler_task: asyncio.Task | None = None
STATISTICS_START_MINUTE = 6 * 60
STATISTICS_END_MINUTE = 24 * 60
STATISTICS_INTERVAL_MINUTES = 15
logger = logging.getLogger("uvicorn.error")


def get_user_store(settings: Settings = Depends(get_settings)) -> UserStore:
    return UserStore(settings.auth_db_path)


def admin_user_response(user) -> AdminUser:
    return AdminUser(
        email=user.email,
        login=user.login,
        role=user.role,
        active=user.active,
        must_change_password=user.must_change_password,
        created_at=user.created_at,
        last_login_at=user.last_login_at,
    )


def admin_target(email: str, users: UserStore):
    account = users.get(email)
    if account is None:
        raise HTTPException(status_code=404, detail="Пользователь не найден")
    return account


async def ensure_application_access(
    application_id: int, user: dict, crm: ClientBaseClient
) -> None:
    try:
        await crm.ensure_application_access(application_id, user["sub"])
    except ClientBaseError as exc:
        upstream_status = exc.status_code or 502
        safe_status = upstream_status if 400 <= upstream_status < 500 else 502
        raise HTTPException(status_code=safe_status, detail=str(exc)) from exc


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


def update_apk(settings: Settings) -> Path:
    return Path(settings.update_apk_path).expanduser().resolve()


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


@app.get("/api/v1/app-update", response_model=AppUpdateInfo)
async def app_update(
    version_code: int = 0,
    settings: Settings = Depends(get_settings),
):
    apk = update_apk(settings)
    if settings.update_version_code <= 0 or not apk.is_file():
        raise HTTPException(status_code=404, detail="Обновление пока не опубликовано")
    return AppUpdateInfo(
        update_available=version_code < settings.update_version_code,
        version_code=settings.update_version_code,
        version_name=settings.update_version_name,
        mandatory=settings.update_mandatory,
        download_url=(
            settings.public_base_url.rstrip("/") + "/api/v1/app-update/apk"
        ),
        sha256=file_sha256(apk),
        file_size=apk.stat().st_size,
        release_notes=settings.update_release_notes,
    )


@app.get("/api/v1/app-update/apk")
async def download_app_update(settings: Settings = Depends(get_settings)):
    apk = update_apk(settings)
    if settings.update_version_code <= 0 or not apk.is_file():
        raise HTTPException(status_code=404, detail="Обновление пока не опубликовано")
    return FileResponse(
        path=apk,
        media_type="application/vnd.android.package-archive",
        filename=f"CUBUS-{settings.update_version_name or 'update'}.apk",
        headers={"Cache-Control": "no-store"},
    )


@app.get("/api/v1/documents", response_model=list[DocumentationItem])
async def documents(user: dict = Depends(current_user)):
    return document_store.list(user["sub"])


@app.post("/api/v1/documents", response_model=DocumentationItem)
async def add_document(command: DocumentationCreate, user: dict = Depends(current_user)):
    try:
        document = document_store.add(user["sub"], command)
        operations_store.log(
            user, "Добавлен документ", entity_type="document",
            entity_id=document.id, details=document.title,
        )
        return document
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/api/v1/documents/{document_id}/content", response_model=DocumentationContent)
async def document_content(document_id: int, user: dict = Depends(current_user)):
    try:
        return document_store.content(user["sub"], document_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Документ не найден") from exc


@app.delete("/api/v1/documents/{document_id}")
async def delete_document(document_id: int, user: dict = Depends(current_user)):
    try:
        document_store.delete(user["sub"], document_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Документ не найден") from exc
    operations_store.log(
        user, "Удалён документ", entity_type="document", entity_id=document_id
    )
    return {"success": True}


@app.get("/api/v1/reports/period", response_model=PeriodReport)
async def period_report(
    date_from: date,
    date_to: date,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    if date_to < date_from:
        raise HTTPException(status_code=400, detail="Дата окончания раньше даты начала")
    if (date_to - date_from).days > 366:
        raise HTTPException(status_code=400, detail="Максимальный период отчёта — 366 дней")
    try:
        return await crm.period_report(user["sub"], date_from, date_to)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/api/v1/auth/register", response_model=TokenResponse)
async def register(request: RegisterRequest, settings: Settings = Depends(get_settings)):
    registration = settings.parsed_registration_codes().get(request.code)
    if registration is None:
        raise HTTPException(status_code=401, detail="Неверный или использованный код")
    login, role = registration
    token = create_access_token(login, role, request.device_name, settings)
    return TokenResponse(access_token=token, login=login, role=role)


@app.post("/api/v1/auth/login", response_model=TokenResponse)
async def login(
    request: LoginRequest,
    settings: Settings = Depends(get_settings),
    users: UserStore = Depends(get_user_store),
):
    user = users.authenticate(request.email, request.password)
    if user is None:
        raise HTTPException(status_code=401, detail="Неверная почта или пароль")
    token = create_access_token(
        user.login,
        user.role,
        request.device_name,
        settings,
        email=user.email,
        session_version=user.session_version,
        must_change_password=user.must_change_password,
    )
    return TokenResponse(
        access_token=token,
        login=user.login,
        role=user.role,
        must_change_password=user.must_change_password,
    )


@app.post("/api/v1/auth/change-password", response_model=TokenResponse)
async def change_password(
    request: ChangePasswordRequest,
    user: dict = Depends(authenticated_user),
    settings: Settings = Depends(get_settings),
    users: UserStore = Depends(get_user_store),
):
    email = user.get("email")
    if not email:
        raise HTTPException(status_code=400, detail="Для этой учётной записи смена пароля недоступна")
    try:
        account = users.change_password(email, request.new_password)
    except (KeyError, ValueError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    token = create_access_token(
        account.login,
        account.role,
        user.get("device", "Android"),
        settings,
        email=account.email,
        session_version=account.session_version,
    )
    return TokenResponse(access_token=token, login=account.login, role=account.role)


@app.get("/api/v1/admin/users", response_model=list[AdminUser])
async def admin_users(
    _admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    return [admin_user_response(user) for user in users.list_users()]


@app.post("/api/v1/admin/users", response_model=AdminUserCreated)
async def admin_create_user(
    request: AdminUserCreateRequest,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    if request.role.casefold() not in {"администратор", "метролог", "диспетчер"}:
        raise HTTPException(status_code=400, detail="Недопустимая роль")
    try:
        account, temporary_password = users.create_with_temporary_password(
            request.email, request.login, request.role, admin["email"]
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return AdminUserCreated(
        user=admin_user_response(account), temporary_password=temporary_password
    )


@app.post("/api/v1/admin/users/{email}/reset-password", response_model=AdminUserCreated)
async def admin_reset_password(
    email: str,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    try:
        account, temporary_password = users.reset_password(email, admin["email"])
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Пользователь не найден") from exc
    return AdminUserCreated(
        user=admin_user_response(account), temporary_password=temporary_password
    )


@app.patch("/api/v1/admin/users/{email}/active", response_model=AdminUser)
async def admin_set_user_active(
    email: str,
    request: AdminUserActiveRequest,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    if email.casefold() == admin["email"].casefold() and not request.active:
        raise HTTPException(status_code=400, detail="Нельзя заблокировать собственную учётную запись")
    try:
        return admin_user_response(users.set_active(email, request.active, admin["email"]))
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Пользователь не найден") from exc


@app.get(
    "/api/v1/admin/users/{email}/workspace",
    response_model=AdminEmployeeWorkspace,
)
async def admin_employee_workspace(
    email: str,
    date_from: date,
    date_to: date,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
    crm: ClientBaseClient = Depends(get_client_base),
):
    if date_to < date_from:
        raise HTTPException(status_code=400, detail="Дата окончания раньше даты начала")
    if (date_to - date_from).days > 366:
        raise HTTPException(status_code=400, detail="Максимальный период отчёта — 366 дней")
    account = admin_target(email, users)
    try:
        report, warehouse, applications = await asyncio.gather(
            crm.period_report(account.login, date_from, date_to),
            crm.metrolog_warehouse(account.login),
            crm.list_applications(account.login),
        )
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    operations_store.sync_many(applications, account.login)
    operations_store.log(
        admin, "Просмотр данных сотрудника", target_login=account.login,
        entity_type="user", entity_id=account.email,
    )
    return AdminEmployeeWorkspace(
        user=admin_user_response(account),
        report=report,
        warehouse=warehouse,
        applications=applications,
        documents=document_store.list(account.login),
        history=operations_store.history(account.login),
    )


@app.get(
    "/api/v1/admin/users/{email}/applications/{application_id}",
    response_model=ApplicationDetails,
)
async def admin_employee_application(
    email: str,
    application_id: int,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
    crm: ClientBaseClient = Depends(get_client_base),
):
    account = admin_target(email, users)
    try:
        await crm.ensure_application_access(application_id, account.login)
        application = await crm.get_application(application_id)
    except ClientBaseError as exc:
        upstream_status = exc.status_code or 502
        raise HTTPException(
            status_code=upstream_status if 400 <= upstream_status < 500 else 502,
            detail=str(exc),
        ) from exc
    operations_store.sync_application(application, account.login, application)
    operations_store.log(
        admin, "Просмотр заявки", target_login=account.login,
        entity_type="application", entity_id=application_id,
    )
    return application


@app.post(
    "/api/v1/admin/users/{email}/documents", response_model=DocumentationItem
)
async def admin_add_employee_document(
    email: str,
    command: DocumentationCreate,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    account = admin_target(email, users)
    try:
        document = document_store.add(account.login, command)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    operations_store.log(
        admin, "Добавлен документ администратором", target_login=account.login,
        entity_type="document", entity_id=document.id, details=document.title,
    )
    return document


@app.put(
    "/api/v1/admin/users/{email}/documents/{document_id}",
    response_model=DocumentationItem,
)
async def admin_replace_employee_document(
    email: str,
    document_id: int,
    command: DocumentationCreate,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    account = admin_target(email, users)
    try:
        document = document_store.replace(account.login, document_id, command)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Документ не найден") from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    operations_store.log(
        admin, "Заменён документ администратором", target_login=account.login,
        entity_type="document", entity_id=document.id, details=document.title,
    )
    return document


@app.get(
    "/api/v1/admin/users/{email}/documents/{document_id}/content",
    response_model=DocumentationContent,
)
async def admin_employee_document_content(
    email: str,
    document_id: int,
    _admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    account = admin_target(email, users)
    try:
        return document_store.content(account.login, document_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Документ не найден") from exc


@app.delete("/api/v1/admin/users/{email}/documents/{document_id}")
async def admin_delete_employee_document(
    email: str,
    document_id: int,
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    account = admin_target(email, users)
    try:
        document_store.delete(account.login, document_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Документ не найден") from exc
    operations_store.log(
        admin, "Удалён документ администратором", target_login=account.login,
        entity_type="document", entity_id=document_id,
    )
    return {"success": True}


@app.get(
    "/api/v1/admin/users/{email}/history", response_model=list[ActivityLogItem]
)
async def admin_employee_history(
    email: str,
    _admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
):
    account = admin_target(email, users)
    return operations_store.history(account.login)


@app.get("/api/v1/admin/registry", response_model=list[RegistryAddress])
async def admin_registry(
    q: str = "",
    limit: int = 200,
    _admin: dict = Depends(current_admin),
):
    return operations_store.addresses(q, limit)


@app.get("/api/v1/admin/registry/stats", response_model=RegistryStats)
async def admin_registry_stats(_admin: dict = Depends(current_admin)):
    return operations_store.stats()


@app.post("/api/v1/admin/registry/sync", response_model=RegistryStats)
async def admin_sync_registry(
    admin: dict = Depends(current_admin),
    users: UserStore = Depends(get_user_store),
    crm: ClientBaseClient = Depends(get_client_base),
):
    failures: list[str] = []
    detail_sources: dict[str, tuple[ApplicationSummary, str]] = {}
    for account in users.list_users():
        if not account.active or account.role.casefold() not in {"метролог", "администратор"}:
            continue
        try:
            applications = await crm.list_applications(account.login)
            operations_store.sync_many(applications, account.login)
            for application in applications:
                address_key = " ".join(application.address.casefold().replace("ё", "е").split())
                if address_key:
                    detail_sources.setdefault(address_key, (application, account.login))
        except ClientBaseError:
            failures.append(account.login)
    semaphore = asyncio.Semaphore(6)

    async def sync_details(application: ApplicationSummary, login: str) -> None:
        async with semaphore:
            try:
                details = await crm.get_application(application.id)
                operations_store.sync_application(details, login, details)
            except ClientBaseError:
                failures.append(f"заявка {application.number}")

    await asyncio.gather(
        *(sync_details(application, login) for application, login in detail_sources.values())
    )
    operations_store.log(
        admin, "Обновлён единый реестр адресов", entity_type="registry",
        details=("Ошибки: " + ", ".join(failures)) if failures else "Успешно",
    )
    return operations_store.stats()


@app.get("/api/v1/applications", response_model=list[ApplicationSummary])
async def applications(
    status_filter: str | None = None,
    work_date: date | None = None,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        if str(user.get("role", "")).casefold() in {"администратор", "admin"}:
            result = await crm.list_company_applications(status_filter, work_date)
        else:
            result = await crm.list_applications(user["sub"], status_filter, work_date)
        operations_store.sync_many(result, user["sub"])
        return result
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get(
    "/api/v1/application-status-counts",
    response_model=list[ApplicationStatusCount],
)
async def application_status_counts(
    work_date: date | None = None,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        statistics_date = work_date or date.today()
        counts = await crm.application_status_counts(user["sub"], statistics_date)
        operations_store.save_daily_statistics(user["sub"], statistics_date, counts)
        return counts
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/daily-statistics", response_model=list[DailyStatistics])
async def daily_statistics(
    days: int = 31,
    user: dict = Depends(current_user),
):
    return operations_store.daily_statistics(user["sub"], days)


async def capture_today_statistics(
    statistics_date: date,
    user: dict,
    crm: ClientBaseClient,
) -> TodayStatistics:
    is_admin = str(user.get("role", "")).casefold() in {"администратор", "admin"}
    scope = "__company_quarter_hour__" if is_admin else user["sub"]
    try:
        current = await (
            crm.list_company_tracking_applications(statistics_date)
            if is_admin
            else crm.list_applications(user["sub"], None, statistics_date)
        )
        current_by_id = {item.id: item for item in current}
        baseline = operations_store.tracking_baseline(scope, statistics_date)
        if not baseline:
            initial = [
                item for item in current
                if canonical_application_status(item.status) == "Новая"
            ]
            baseline = operations_store.create_tracking_baseline(
                scope, statistics_date, initial
            )
        elif is_admin:
            added = [
                item for item in current
                if item.id not in baseline
                and canonical_application_status(item.status) == "Новая"
            ]
            if added:
                operations_store.create_tracking_baseline(
                    scope, statistics_date, added, is_initial=False
                )
                baseline = operations_store.tracking_baseline(scope, statistics_date)

        initial_ids = operations_store.tracking_initial_ids(scope, statistics_date)
        initial_new_ids = initial_ids if is_admin else {
            item_id for item_id, initial_status in baseline.items()
            if canonical_application_status(initial_status) == "Новая"
        }
        initial_dates = operations_store.tracking_initial_dates(scope, statistics_date)
        tracked_ids = set(baseline)
        missing_ids = [item_id for item_id in tracked_ids if item_id not in current_by_id]

        async def load_missing(item_id: int):
            try:
                return await (
                    crm.get_application_tracking_state(item_id)
                    if is_admin else crm.get_application(item_id)
                )
            except ClientBaseError:
                return None

        missing = await asyncio.gather(*(load_missing(item_id) for item_id in missing_ids))
        missing_by_id = {item.id: item for item in missing if item is not None}
        tracked_by_id = {**current_by_id, **missing_by_id}
        status_counts = {
            "Новая": 0,
            "Выполнено": 0,
            "На Доработку": 0,
            "Отложено": 0,
            "Отказ": 0,
        }
        moved_ids = {
            item_id for item_id, item in tracked_by_id.items()
            if initial_dates.get(item_id) != item.work_date
        }
        for item in tracked_by_id.values():
            if is_admin and item.id in moved_ids:
                continue
            canonical = canonical_application_status(item.status)
            if canonical in status_counts:
                status_counts[canonical] += 1

        moved = len(moved_ids)
        transferred = 0 if is_admin else sum(
            1 for item_id, item in missing_by_id.items()
            if item_id in initial_new_ids and item.work_date == statistics_date
        )
        added_later = len(tracked_ids - initial_ids)
        result = TodayStatistics(
            work_date=statistics_date,
            initial_new=len(initial_new_ids),
            completed=status_counts["Выполнено"],
            rework=status_counts["На Доработку"],
            postponed=status_counts["Отложено"],
            refusals=status_counts["Отказ"],
            remaining_new=status_counts["Новая"],
            moved_to_other_date=moved,
            transferred_to_other_employee=transferred,
            added_later=added_later,
            snapshot_at=datetime.now(UTC).isoformat(),
        )
        return operations_store.save_tracking_snapshot(scope, result)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


def statistics_scope(user: dict) -> str:
    is_admin = str(user.get("role", "")).casefold() in {"администратор", "admin"}
    return "__company_quarter_hour__" if is_admin else user["sub"]


def statistics_subjects(users: UserStore) -> list[dict]:
    return [
        {"sub": "__company__", "role": "Администратор", "email": ""}
    ]


async def capture_scheduled_statistics(
    statistics_date: date,
    scheduled_hour: int,
    crm: ClientBaseClient,
    users: UserStore,
) -> None:
    for subject in statistics_subjects(users):
        scope = statistics_scope(subject)
        if operations_store.scheduled_capture_done(
            scope, statistics_date, scheduled_hour
        ):
            continue
        try:
            await capture_today_statistics(statistics_date, subject, crm)
            operations_store.mark_scheduled_capture_done(
                scope, statistics_date, scheduled_hour
            )
        except Exception as exc:
            logger.warning(
                "Statistics capture failed: scope=%s date=%s slot=%s error=%s",
                scope, statistics_date, scheduled_hour, exc,
            )


def latest_statistics_slot(now_moscow: datetime) -> tuple[date, int]:
    minute_of_day = now_moscow.hour * 60 + now_moscow.minute
    if minute_of_day < STATISTICS_START_MINUTE:
        return now_moscow.date() - timedelta(days=1), STATISTICS_END_MINUTE
    slot = minute_of_day - (minute_of_day % STATISTICS_INTERVAL_MINUTES)
    return now_moscow.date(), min(slot, STATISTICS_END_MINUTE - STATISTICS_INTERVAL_MINUTES)


async def statistics_scheduler() -> None:
    settings = get_settings()
    crm = ClientBaseClient(settings, load_field_mapping())
    users = UserStore(settings.auth_db_path)
    moscow = ZoneInfo("Europe/Moscow")

    now = datetime.now(moscow)
    for subject in statistics_subjects(users):
        scope = statistics_scope(subject)
        if operations_store.latest_tracking_snapshot(scope, now.date()) is None:
            try:
                await capture_today_statistics(now.date(), subject, crm)
            except Exception as exc:
                logger.warning(
                    "Initial statistics capture failed: scope=%s error=%s", scope, exc
                )

    while True:
        now = datetime.now(moscow)
        statistics_date, scheduled_hour = latest_statistics_slot(now)
        await capture_scheduled_statistics(
            statistics_date, scheduled_hour, crm, users
        )
        await asyncio.sleep(60)


@app.on_event("startup")
async def start_statistics_scheduler() -> None:
    global statistics_scheduler_task
    if get_settings().statistics_scheduler_enabled:
        statistics_scheduler_task = asyncio.create_task(statistics_scheduler())


@app.on_event("shutdown")
async def stop_statistics_scheduler() -> None:
    global statistics_scheduler_task
    if statistics_scheduler_task is not None:
        statistics_scheduler_task.cancel()
        try:
            await statistics_scheduler_task
        except asyncio.CancelledError:
            pass
        statistics_scheduler_task = None


@app.get("/api/v1/today-statistics", response_model=TodayStatistics)
async def today_statistics(
    work_date: date | None = None,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    statistics_date = work_date or date.today()
    is_admin = str(user.get("role", "")).casefold() in {"администратор", "admin"}
    scope = "__company__" if is_admin else user["sub"]
    existing = operations_store.latest_tracking_snapshot(scope, statistics_date)
    if existing is not None:
        return existing
    return await capture_today_statistics(statistics_date, user, crm)


@app.post("/api/v1/today-statistics/capture", response_model=TodayStatistics)
async def capture_today_statistics_endpoint(
    work_date: date | None = None,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    return await capture_today_statistics(work_date or date.today(), user, crm)


@app.get("/api/v1/today-statistics/history", response_model=list[TodayStatistics])
async def today_statistics_history(
    work_date: date | None = None,
    user: dict = Depends(current_user),
):
    is_admin = str(user.get("role", "")).casefold() in {"администратор", "admin"}
    scope = "__company__" if is_admin else user["sub"]
    return operations_store.tracking_snapshots(
        scope, work_date or date.today()
    )


@app.get("/api/v1/application-map-points", response_model=list[ApplicationMapPoint])
async def application_map_points(
    work_date: date | None = None,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.application_map_points(user["sub"], work_date)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/profile", response_model=EmployeeProfile)
async def profile(
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.employee_profile(
            user["sub"], user.get("role", ""), user.get("device", "")
        )
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/api/v1/profile/home-address", response_model=HomeAddress)
async def save_home_address(
    request: HomeAddressRequest,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.save_home_address(user["sub"], request.address)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/api/v1/profile/administrative-expenses", response_model=EmployeeProfile)
async def save_administrative_expenses(
    request: AdministrativeExpensesRequest,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        crm.save_administrative_expenses(
            user["sub"], request.administrative_expenses
        )
        return await crm.employee_profile(
            user["sub"], user.get("role", ""), user.get("device", "")
        )
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/weather", response_model=WeatherSnapshot)
async def weather(
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.current_weather(user["sub"])
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/schedule", response_model=list[ScheduleDay])
async def schedule(
    year: int,
    month: int,
    refresh: bool = False,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    if month < 1 or month > 12:
        raise HTTPException(status_code=422, detail="Некорректный месяц")
    try:
        return await crm.employee_schedule(user["sub"], year, month, refresh=refresh)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/material-usage", response_model=list[MaterialUsageItem])
async def material_usage(
    work_date: date,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.material_usage(user["sub"], work_date)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/metrolog-warehouse", response_model=list[WarehouseItem])
async def metrolog_warehouse(
    refresh: bool = False,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.metrolog_warehouse(user["sub"], force_refresh=refresh)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/applications/{application_id}", response_model=ApplicationDetails)
async def application(
    application_id: int,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        result = await crm.get_application(application_id)
        operations_store.sync_application(result, user["sub"], result)
        operations_store.log(
            user, "Открыта заявка", entity_type="application", entity_id=application_id
        )
        return result
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/api/v1/applications/{application_id}/photos")
async def upload_application_photo(
    application_id: int,
    request: UploadPhotoRequest,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        await crm.upload_photo(
            application_id, request.field, request.filename, request.content_base64
        )
        operations_store.log(
            user, "Добавлен файл в заявку", entity_type="application",
            entity_id=application_id, details=request.filename,
        )
        return {"success": True}
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get(
    "/api/v1/applications/{application_id}/photos/content",
    response_model=PhotoContent,
)
async def application_photo_content(
    application_id: int,
    field: str,
    filename: str,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        return PhotoContent(
            content_base64=await crm.photo_content(application_id, field, filename)
        )
    except ClientBaseError as exc:
        upstream_status = exc.status_code or 502
        safe_status = upstream_status if 400 <= upstream_status < 500 else 502
        raise HTTPException(status_code=safe_status, detail=str(exc)) from exc


@app.get(
    "/api/v1/applications/{application_id}/nomenclature",
    response_model=list[NomenclatureItem],
)
async def application_nomenclature(
    application_id: int,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        return await crm.nomenclature(application_id)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/price-list", response_model=list[PriceListItem])
async def price_list(
    refresh: bool = False,
    _: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.price_list(force_refresh=refresh)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/api/v1/applications/{application_id}/nomenclature")
async def add_application_nomenclature(
    application_id: int,
    command: AddNomenclatureRequest,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        await crm.add_nomenclature(application_id, command)
        operations_store.log(
            user, "Добавлена номенклатура", entity_type="application",
            entity_id=application_id, details=str(command.price_list_id),
        )
        return {"success": True}
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.delete("/api/v1/applications/{application_id}/nomenclature/{nomenclature_id}")
async def delete_application_nomenclature(
    application_id: int,
    nomenclature_id: int,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        await crm.delete_nomenclature(application_id, nomenclature_id)
        operations_store.log(
            user, "Удалена номенклатура", entity_type="application",
            entity_id=application_id, details=str(nomenclature_id),
        )
        return {"success": True}
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/api/v1/meter-catalog", response_model=list[MeterCatalogItem])
async def meter_catalog(
    q: str = "",
    refresh: bool = False,
    _: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.meter_catalog(q, force_refresh=refresh)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post(
    "/api/v1/applications/{application_id}/meters",
    response_model=WaterMeter,
)
async def add_application_meter(
    application_id: int,
    command: AddMeterRequest,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        result = await crm.add_meter(application_id, command)
        operations_store.log(
            user, "Добавлен прибор учёта", entity_type="application",
            entity_id=application_id, details=command.serial_number,
        )
        return result
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.patch(
    "/api/v1/applications/{application_id}/meters/{meter_id}",
    response_model=WaterMeter,
)
async def update_application_meter(
    application_id: int,
    meter_id: int,
    command: AddMeterRequest,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        result = await crm.update_meter(application_id, meter_id, command)
        operations_store.log(
            user, "Изменён прибор учёта", entity_type="meter",
            entity_id=meter_id, details=command.serial_number,
        )
        return result
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.delete("/api/v1/applications/{application_id}/meters/{meter_id}")
async def delete_application_meter(
    application_id: int,
    meter_id: int,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        await crm.delete_meter(application_id, meter_id)
        operations_store.log(
            user, "Удалён прибор учёта", entity_type="meter", entity_id=meter_id
        )
        return {"success": True}
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.delete("/api/v1/applications/{application_id}/photos")
async def delete_application_photo(
    application_id: int,
    field: str,
    filename: str,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    try:
        await crm.delete_photo(application_id, field, filename)
        operations_store.log(
            user, "Удалён файл из заявки", entity_type="application",
            entity_id=application_id, details=filename,
        )
        return {"success": True}
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post(
    "/api/v1/applications/{application_id}/close",
    response_model=OperationResult,
    status_code=status.HTTP_200_OK,
)
async def close_application(
    application_id: int,
    command: CloseApplicationRequest,
    idempotency_key: str = Header(min_length=16, max_length=100),
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    if idempotency_key in completed_operations:
        return completed_operations[idempotency_key]
    try:
        await crm.close_application(application_id, command)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    result = OperationResult(
        success=True, application_id=application_id, status="Выполнено"
    )
    completed_operations[idempotency_key] = result
    operations_store.log(
        user, "Заявка выполнена", entity_type="application", entity_id=application_id
    )
    return result


@app.post(
    "/api/v1/applications/{application_id}/rework",
    response_model=OperationResult,
)
async def send_application_to_rework(
    application_id: int,
    command: ReworkRequest,
    idempotency_key: str = Header(min_length=16, max_length=100),
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    await ensure_application_access(application_id, user, crm)
    operation_key = f"rework:{idempotency_key}"
    if operation_key in completed_operations:
        return completed_operations[operation_key]
    try:
        await crm.send_to_rework(application_id, command.reason, command.comment)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    result = OperationResult(
        success=True, application_id=application_id, status="На Доработку"
    )
    completed_operations[operation_key] = result
    operations_store.log(
        user, "Заявка отправлена на доработку", entity_type="application",
        entity_id=application_id, details=command.reason,
    )
    return result


@app.get("/api/v1/rework-reasons", response_model=list[str])
async def rework_reasons(
    _: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.rework_reasons()
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
