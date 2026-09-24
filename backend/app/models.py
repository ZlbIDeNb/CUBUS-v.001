from datetime import date
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, Field


class PaymentType(StrEnum):
    CASH = "Наличные"
    CARD = "Эквайринг"
    MIXED = "Эквайринг + Наличные"
    CONTRACT = "По договору"


class RegisterRequest(BaseModel):
    code: str = Field(min_length=4, max_length=100)
    device_name: str = Field(min_length=1, max_length=120)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    login: str
    role: str


class ApplicationSummary(BaseModel):
    id: int
    number: str
    work_date: date | None = None
    address: str = ""
    client: str = ""
    interval: str = ""
    status: str


class ApplicationStatusCount(BaseModel):
    status: str
    count: int = Field(ge=0)


class EmployeeProfile(BaseModel):
    login: str
    role: str
    device_name: str
    full_name: str = ""
    position: str = ""
    phone: str = ""
    work_schedule: str = ""
    max_applications: str = ""
    folder_number: str = ""


class WaterMeter(BaseModel):
    id: int
    device_kind: str = ""
    meter_type: str = ""
    modification: str = ""
    accuracy_class: str = ""
    serial_number: str = ""
    registry_number: str = ""
    year: str = ""
    last_check: str = ""
    next_check: str = ""
    status: str = ""
    reading: str = ""


class ApplicationDetails(ApplicationSummary):
    phone_number: str = ""
    phone_number_2: str = ""
    floor: str = ""
    entrance: str = ""
    entrance_code: str = ""
    barrier: str = ""
    comments: str = ""
    metrolog_comments: str = ""
    water_meters: list[WaterMeter] = Field(default_factory=list)


class CloseApplicationRequest(BaseModel):
    payment_type: PaymentType
    cash_sum: Decimal = Field(default=Decimal("0"), ge=0)
    card_sum: Decimal = Field(default=Decimal("0"), ge=0)

    def crm_attributes(self) -> dict[str, str | int]:
        result: dict[str, str | int] = {
            "status": "Выполнено",
            "payment_type": self.payment_type.value,
        }
        if self.payment_type in {PaymentType.CASH, PaymentType.MIXED}:
            result["cash_sum"] = int(self.cash_sum)
        if self.payment_type in {PaymentType.CARD, PaymentType.MIXED}:
            result["card_sum"] = int(self.card_sum)
        return result


class OperationResult(BaseModel):
    success: bool
    application_id: int
    status: str
