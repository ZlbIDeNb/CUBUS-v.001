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
    role: str


class ApplicationSummary(BaseModel):
    id: int
    number: str
    work_date: date | None = None
    address: str = ""
    client: str = ""
    interval: str = ""
    status: str


class ApplicationDetails(ApplicationSummary):
    phone_number: str = ""
    floor: str = ""
    entrance: str = ""
    entrance_code: str = ""
    barrier: str = ""
    comments: str = ""


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

