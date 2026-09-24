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
    delivery_time: str = ""
    status: str
    phone_number: str = ""
    phone_number_2: str = ""
    barrier: str = ""
    comments: str = ""


class ApplicationStatusCount(BaseModel):
    status: str
    count: int = Field(ge=0)


class ApplicationMapPoint(BaseModel):
    application_id: int
    number: str
    address: str
    interval: str = ""
    delivery_time: str = ""
    phone_number: str = ""
    client: str = ""
    comments: str = ""
    latitude: float
    longitude: float


class ScheduleDay(BaseModel):
    date: date
    day: int
    is_working: bool
    has_record: bool = False
    work_status: str = ""


class MaterialUsageItem(BaseModel):
    name: str
    quantity: str = ""
    total: str = ""


class WarehouseItem(BaseModel):
    id: int
    name: str = ""
    incoming: str = ""
    outgoing: str = ""
    balance: str = ""
    written_off_to_warehouse: str = ""
    defect_quantity: str = ""
    defect_position: str = ""
    writeoff_goods_quantity: str = ""
    service_writeoff_quantity: str = ""
    total_written_off: str = ""


class EmployeeEquipment(BaseModel):
    category: str
    name: str = ""
    serial_number: str = ""
    registry_number: str = ""
    certificate_number: str = ""
    verification_date: str = ""
    arshin_url: str = ""


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
    home_address: str = ""
    home_latitude: float | None = None
    home_longitude: float | None = None
    administrative_expenses: str = "Нет"
    equipment: list[EmployeeEquipment] = Field(default_factory=list)


class HomeAddressRequest(BaseModel):
    address: str = Field(min_length=3, max_length=500)


class AdministrativeExpensesRequest(BaseModel):
    administrative_expenses: str = Field(pattern="^(Да|Нет)$")


class HomeAddress(BaseModel):
    address: str
    latitude: float
    longitude: float


class WeatherSnapshot(BaseModel):
    city: str = "г. Москва"
    temperature: float | None = None
    humidity: float | None = None
    pressure_mm_hg: float | None = None


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
    replacement: str = ""
    reading: str = ""
    device_photo: str = ""
    passport_photo: str = ""


class ApplicationPhoto(BaseModel):
    field: str
    title: str
    filename: str
    content_base64: str = ""


class PhotoContent(BaseModel):
    content_base64: str = ""


class DocumentationCreate(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    comment: str = Field(default="", max_length=4000)
    filename: str = Field(min_length=1, max_length=500)
    mime_type: str
    content_base64: str


class DocumentationItem(BaseModel):
    id: int
    title: str
    comment: str = ""
    filename: str
    mime_type: str
    created_at: str


class DocumentationContent(BaseModel):
    filename: str
    mime_type: str
    content_base64: str


class ReportLine(BaseModel):
    name: str
    quantity: Decimal = Decimal("0")
    total: Decimal = Decimal("0")
    unit_price: Decimal = Decimal("0")
    metrologist_gross: Decimal = Decimal("0")
    bank_commission: Decimal = Decimal("0")
    administrative_expenses: Decimal = Decimal("0")
    metrologist_net: Decimal = Decimal("0")
    company: Decimal = Decimal("0")


class PeriodReport(BaseModel):
    date_from: date
    date_to: date
    applications_count: int = 0
    total: Decimal = Decimal("0")
    cash: Decimal = Decimal("0")
    card: Decimal = Decimal("0")
    bank_commission: Decimal = Decimal("0")
    administrative_expenses: Decimal = Decimal("0")
    administrative_expenses_status: str = "Нет"
    metrologist_gross: Decimal = Decimal("0")
    metrologist_net: Decimal = Decimal("0")
    company: Decimal = Decimal("0")
    services: list[ReportLine] = Field(default_factory=list)
    materials: list[ReportLine] = Field(default_factory=list)


class NomenclatureItem(BaseModel):
    id: int
    name: str = ""
    price: str = ""
    quantity: str = ""
    total: str = ""


class PriceListItem(BaseModel):
    id: int
    name: str = ""
    price: str = ""
    item_kind: str = ""


class MeterCatalogItem(BaseModel):
    id: int
    registry_number: str = ""
    designation: str = ""


class AddNomenclatureRequest(BaseModel):
    price_list_id: int
    quantity: int = Field(default=1, ge=1)
    total: Decimal | None = Field(default=None, ge=0)


class AddMeterRequest(BaseModel):
    device_kind: str
    ipu_status: str = "Годен"
    replacement_done: bool = False
    meter_type: str = ""
    serial_number: str = ""
    registry_number: str = ""
    year: str = ""
    last_check: str = ""
    next_check: str = ""
    device_photo_filename: str = ""
    device_photo_base64: str = ""
    passport_photo_filename: str = ""
    passport_photo_base64: str = ""


class UploadPhotoRequest(BaseModel):
    field: str
    filename: str
    content_base64: str


class ApplicationDetails(ApplicationSummary):
    phone_number_2: str = ""
    floor: str = ""
    entrance: str = ""
    entrance_code: str = ""
    metrolog_comments: str = ""
    water_meters: list[WaterMeter] = Field(default_factory=list)
    photos: list[ApplicationPhoto] = Field(default_factory=list)
    nomenclature: list[NomenclatureItem] = Field(default_factory=list)


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


class ReworkRequest(BaseModel):
    reason: str = Field(min_length=1, max_length=500)
    comment: str = Field(min_length=1, max_length=4000)


class OperationResult(BaseModel):
    success: bool
    application_id: int
    status: str
