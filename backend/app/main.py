from datetime import date

from fastapi import Depends, FastAPI, Header, HTTPException, status

from .auth import create_access_token, current_user
from .client_base import ClientBaseClient, ClientBaseError
from .config import Settings, get_settings
from .dependencies import get_client_base
from .models import (
    ApplicationDetails,
    ApplicationStatusCount,
    ApplicationSummary,
    CloseApplicationRequest,
    EmployeeProfile,
    OperationResult,
    RegisterRequest,
    TokenResponse,
)


app = FastAPI(title="Zilisnik Mobile API", version="0.1.0")
completed_operations: dict[str, OperationResult] = {}


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/v1/auth/register", response_model=TokenResponse)
async def register(request: RegisterRequest, settings: Settings = Depends(get_settings)):
    registration = settings.parsed_registration_codes().get(request.code)
    if registration is None:
        raise HTTPException(status_code=401, detail="Неверный или использованный код")
    login, role = registration
    token = create_access_token(login, role, request.device_name, settings)
    return TokenResponse(access_token=token, login=login, role=role)


@app.get("/api/v1/applications", response_model=list[ApplicationSummary])
async def applications(
    status_filter: str | None = None,
    work_date: date | None = None,
    user: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.list_applications(user["sub"], status_filter, work_date)
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
        return await crm.application_status_counts(user["sub"], work_date)
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


@app.get("/api/v1/applications/{application_id}", response_model=ApplicationDetails)
async def application(
    application_id: int,
    _: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    try:
        return await crm.get_application(application_id)
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
    _: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
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
    return result


@app.post(
    "/api/v1/applications/{application_id}/rework",
    response_model=OperationResult,
)
async def send_application_to_rework(
    application_id: int,
    idempotency_key: str = Header(min_length=16, max_length=100),
    _: dict = Depends(current_user),
    crm: ClientBaseClient = Depends(get_client_base),
):
    operation_key = f"rework:{idempotency_key}"
    if operation_key in completed_operations:
        return completed_operations[operation_key]
    try:
        await crm.send_to_rework(application_id)
    except ClientBaseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    result = OperationResult(
        success=True, application_id=application_id, status="На Доработку"
    )
    completed_operations[operation_key] = result
    return result
