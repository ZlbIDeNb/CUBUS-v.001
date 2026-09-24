import asyncio
import base64
import calendar
from datetime import date
from decimal import Decimal, InvalidOperation
import re
from typing import Any

import httpx

from .config import Settings
from .models import (
    ApplicationDetails,
    ApplicationPhoto,
    ApplicationStatusCount,
    ApplicationSummary,
    AddMeterRequest,
    AddNomenclatureRequest,
    CloseApplicationRequest,
    EmployeeEquipment,
    EmployeeProfile,
    NomenclatureItem,
    MeterCatalogItem,
    MaterialUsageItem,
    PriceListItem,
    ScheduleDay,
    WaterMeter,
)


PHOTO_FIELDS = {
    "photo_act": "Акт поверки",
    "photo_replacement_act": "Акт замены",
    "photo_receipt": "Квитанция",
    "photo_invoice": "Счёт-договор",
    "photo_meters": "Счётчики",
    "photo_cash_receipt": "Кассовый чек",
    "photo_control_readings": "Акт контрольного снятия показаний",
}


APPLICATION_STATUSES = (
    "Новая",
    "Выполнено",
    "На Доработку",
)


class ClientBaseError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


class ClientBaseClient:
    """Client Base adapter. Field mapping is injected to avoid hard-coding PROD ids."""

    def __init__(self, settings: Settings, fields: dict[str, str]):
        account_url = settings.client_base_url.rstrip("/")
        self.base_url = (
            account_url
            if account_url.endswith("/api/dev")
            else f"{account_url}/api/dev"
        )
        self.timeout = settings.client_base_timeout_seconds
        self.headers = {
            "Accept": "application/vnd.api+json",
            "Content-Type": "application/vnd.api+json",
            "X-Auth-Token": settings.client_base_token.get_secret_value(),
        }
        self.fields = fields

    async def _request(self, method: str, path: str, **kwargs: Any) -> dict:
        headers = dict(self.headers)
        try:
            async with httpx.AsyncClient(
                timeout=self.timeout, follow_redirects=True
            ) as client:
                response = await client.request(
                    method,
                    f"{self.base_url}/{path.lstrip('/')}",
                    headers=headers,
                    **kwargs,
                )
            response.raise_for_status()
            return response.json() if response.content else {}
        except httpx.HTTPStatusError as exc:
            raise ClientBaseError(
                f"Client Base вернул HTTP {exc.response.status_code}",
                status_code=exc.response.status_code,
            ) from exc
        except (httpx.HTTPError, ValueError) as exc:
            raise ClientBaseError("Client Base не подтвердил операцию") from exc

    async def list_applications(
        self,
        crm_login: str,
        application_status: str | None = None,
        work_date: date | None = None,
    ) -> list[ApplicationSummary]:
        # Endpoint mapping follows the legacy bot: employee=data46, application=data130.
        employee_id = await self._employee_id(crm_login)
        if employee_id is None:
            return []
        metrolog_field = self.fields["application_metrolog_id"]
        conditions = ["eq(status,0)", f"eq({metrolog_field},{employee_id})"]
        if application_status:
            safe_status = application_status.replace("'", "\\'")
            conditions.append(f"eq({self.fields['status']},'{safe_status}')")
        if work_date:
            date_field = self.fields["work_date"]
            day = work_date.isoformat()
            conditions.extend(
                [
                    f"gte({date_field},'{day} 00:00:00')",
                    f"lte({date_field},'{day} 23:59:59')",
                ]
            )
        application_rows = await self._list_all(
            "data130",
            filter_expression=f"and({','.join(conditions)})",
        )
        applications = [await self._summary(item) for item in application_rows]
        return sorted(
            applications,
            key=lambda item: (
                not bool(item.delivery_time.strip()),
                item.delivery_time.strip(),
                item.number,
            ),
        )

    async def application_status_counts(
        self, crm_login: str, work_date: date | None = None
    ) -> list[ApplicationStatusCount]:
        employee_id = await self._employee_id(crm_login)
        counts = {status: 0 for status in APPLICATION_STATUSES}
        if employee_id is not None:
            conditions = [
                "eq(status,0)",
                f"eq({self.fields['application_metrolog_id']},{employee_id})",
            ]
            if work_date:
                date_field = self.fields["work_date"]
                day = work_date.isoformat()
                conditions.extend(
                    [
                        f"gte({date_field},'{day} 00:00:00')",
                        f"lte({date_field},'{day} 23:59:59')",
                    ]
                )
            rows = await self._list_all(
                "data130",
                filter_expression=f"and({','.join(conditions)})",
            )
            status_field = self.fields["status"]
            for row in rows:
                value = str(row.get("attributes", {}).get(status_field, "") or "")
                if value in counts:
                    counts[value] += 1
        return [ApplicationStatusCount(status=name, count=counts[name]) for name in APPLICATION_STATUSES]

    async def _employee_id(self, crm_login: str) -> Any | None:
        row = await self._employee_row(crm_login)
        if row is None:
            return None
        return row.get("attributes", {}).get(self.fields["employee_id"])

    async def _employee_row(self, crm_login: str) -> dict | None:
        employee_field = self.fields["employee_login"]
        safe_login = crm_login.replace("'", "\\'")
        condition = f"and(eq(status,0),eq({employee_field},'{safe_login}'))"
        employee = await self._request(
            "GET",
            "data46",
            params={"filter": condition, "page[limit]": 1, "page[offset]": 0},
        )
        rows = employee.get("data", [])
        if not rows:
            return None
        return rows[0]

    async def employee_profile(
        self, crm_login: str, role: str, device_name: str
    ) -> EmployeeProfile:
        row = await self._employee_row(crm_login)
        attrs = row.get("attributes", {}) if row else {}
        return EmployeeProfile(
            login=crm_login,
            role=role,
            device_name=device_name,
            full_name=str(attrs.get(self.fields["employee_name"], "") or ""),
            position=str(attrs.get(self.fields["employee_position"], "") or ""),
            phone=str(attrs.get(self.fields["employee_phone"], "") or ""),
            work_schedule=str(
                attrs.get(self.fields["employee_work_schedule"], "") or ""
            ),
            max_applications=str(
                attrs.get(self.fields["employee_max_applications"], "") or ""
            ),
            folder_number=str(
                attrs.get(self.fields["employee_folder_number"], "") or ""
            ),
            equipment=self._employee_equipment(attrs),
        )

    async def employee_schedule(
        self, crm_login: str, year: int, month: int
    ) -> list[ScheduleDay]:
        row = await self._employee_row(crm_login)
        attrs = row.get("attributes", {}) if row else {}
        work_days = str(attrs.get(self.fields["employee_work_days"], "") or "")
        schedule = str(
            attrs.get(self.fields["employee_work_schedule"], "") or ""
        )
        control = str(
            attrs.get(self.fields["employee_schedule_control"], "") or ""
        )
        source = f"{work_days} {schedule} {control}".casefold()
        explicit_dates = {
            date(int(y), int(m), int(d))
            for y, m, d in re.findall(r"(\d{4})-(\d{2})-(\d{2})", source)
        }
        explicit_dates.update(
            date(int(y), int(m), int(d))
            for d, m, y in re.findall(r"(\d{2})\.(\d{2})\.(\d{4})", source)
        )
        weekday_names = {
            0: ("понедельник", "пн"), 1: ("вторник", "вт"),
            2: ("среда", "ср"), 3: ("четверг", "чт"),
            4: ("пятница", "пт"), 5: ("суббота", "сб"),
            6: ("воскресенье", "вс"),
        }
        selected_weekdays = {
            weekday
            for weekday, names in weekday_names.items()
            if any(re.search(rf"\b{re.escape(name)}\b", source) for name in names)
        }
        _, last_day = calendar.monthrange(year, month)
        result: list[ScheduleDay] = []
        for day_number in range(1, last_day + 1):
            current = date(year, month, day_number)
            if explicit_dates:
                working = current in explicit_dates
            elif selected_weekdays:
                working = current.weekday() in selected_weekdays
            elif "2/2" in source or "2 через 2" in source:
                working = ((current - date(2020, 1, 1)).days % 4) < 2
            elif "6/1" in source:
                working = current.weekday() < 6
            else:
                working = current.weekday() < 5
            result.append(ScheduleDay(date=current, day=day_number, is_working=working))
        return result

    async def material_usage(
        self, crm_login: str, work_date: date
    ) -> list[MaterialUsageItem]:
        applications = await self.list_applications(crm_login, work_date=work_date)
        totals: dict[str, tuple[Decimal, Decimal]] = {}
        price_cache: dict[int, tuple[str, str]] = {}
        for application in applications:
            relation_field = self.fields["nomenclature_application_id"]
            rows = await self._list_all(
                "data351",
                filter_expression=(
                    f"and(eq(status,0),eq({relation_field},{application.id}))"
                ),
            )
            for row in rows:
                attrs = row.get("attributes", {}) or {}
                relation_value = attrs.get(self.fields["nomenclature_name"], "")
                match = re.search(r"\d+", str(relation_value))
                if not match:
                    continue
                price_id = int(match.group())
                if price_id not in price_cache:
                    price_row = await self._request("GET", f"data91/{price_id}")
                    price_attrs = (price_row.get("data", {}) or {}).get("attributes", {}) or {}
                    price_cache[price_id] = (
                        str(price_attrs.get(self.fields["price_list_name"], "") or ""),
                        str(price_attrs.get(self.fields["price_list_type"], "") or ""),
                    )
                name, item_type = price_cache[price_id]
                if item_type.casefold() != "товар":
                    continue
                try:
                    quantity = Decimal(
                        str(attrs.get(self.fields["nomenclature_quantity"], "0") or "0").replace(",", ".")
                    )
                    total = Decimal(
                        str(attrs.get(self.fields["nomenclature_total"], "0") or "0").replace(",", ".")
                    )
                except InvalidOperation:
                    continue
                old_quantity, old_total = totals.get(name, (Decimal("0"), Decimal("0")))
                totals[name] = (old_quantity + quantity, old_total + total)
        return [
            MaterialUsageItem(name=name, quantity=str(quantity), total=str(total))
            for name, (quantity, total) in sorted(totals.items(), key=lambda item: item[0].casefold())
        ]

    def _employee_equipment(self, attrs: dict[str, Any]) -> list[EmployeeEquipment]:
        def value(key: str) -> str:
            return str(attrs.get(self.fields[key], "") or "")

        return [
            EmployeeEquipment(
                category="Поверочная установка",
                name=value("equipment_installation_name"),
                serial_number=value("equipment_installation_serial"),
                registry_number=value("equipment_installation_registry"),
                certificate_number=value("equipment_installation_certificate"),
                verification_date=value("equipment_installation_date"),
                arshin_url=value("equipment_installation_arshin"),
            ),
            EmployeeEquipment(
                category="Секундомер",
                name=value("equipment_stopwatch_name"),
                serial_number=value("equipment_stopwatch_serial"),
                registry_number=value("equipment_stopwatch_registry"),
                verification_date=value("equipment_stopwatch_date"),
                arshin_url=value("equipment_stopwatch_arshin"),
            ),
            EmployeeEquipment(
                category="Термогигрометр",
                name=value("equipment_hygrometer_name"),
                serial_number=value("equipment_hygrometer_serial"),
                registry_number=value("equipment_hygrometer_registry"),
                verification_date=value("equipment_hygrometer_date"),
                arshin_url=value("equipment_hygrometer_arshin"),
            ),
            EmployeeEquipment(
                category="Термометр",
                name=value("equipment_thermometer_name"),
                serial_number=value("equipment_thermometer_serial"),
                registry_number=value("equipment_thermometer_registry"),
                verification_date=value("equipment_thermometer_date"),
                arshin_url=value("equipment_thermometer_arshin"),
            ),
            EmployeeEquipment(
                category="Эквайринг",
                name=value("equipment_acquiring_name"),
                serial_number=value("equipment_acquiring_serial"),
            ),
        ]

    async def _list_all(
        self,
        path: str,
        *,
        filter_expression: str | None = None,
        page_limit: int = 50,
    ) -> list[dict]:
        rows: list[dict] = []
        offset = 0
        while True:
            params: dict[str, str | int] = {
                "page[limit]": page_limit,
                "page[offset]": offset,
            }
            if filter_expression:
                params["filter"] = filter_expression
            payload = await self._request("GET", path, params=params)
            page = payload.get("data", []) or []
            rows.extend(page)
            if len(page) < page_limit:
                break
            offset += len(page)
        return rows

    async def get_application(self, application_id: int) -> ApplicationDetails:
        payload = await self._request("GET", f"data130/{application_id}")
        item = payload["data"]
        attrs = item["attributes"]
        summary = await self._summary(item)
        address_id = attrs.get(self.fields["address_id"])
        return ApplicationDetails(
            **summary.model_dump(),
            phone_number_2=str(
                attrs.get(self.fields["phone_number_2"], "") or ""
            ),
            floor=str(attrs.get(self.fields["floor"], "") or ""),
            entrance=str(attrs.get(self.fields["entrance"], "") or ""),
            entrance_code=str(attrs.get(self.fields["entrance_code"], "") or ""),
            metrolog_comments=str(
                attrs.get(self.fields["metrolog_comments"], "") or ""
            ),
            water_meters=await self._water_meters_by_address(address_id),
            photos=await self._application_photos(application_id, attrs),
            nomenclature=[],
        )

    async def nomenclature(self, application_id: int) -> list[NomenclatureItem]:
        return await self._nomenclature(application_id)

    async def price_list(self) -> list[PriceListItem]:
        rows = await self._list_all("data91", filter_expression="eq(status,0)")
        return [
            PriceListItem(
                id=int(row["id"]),
                name=str(
                    (row.get("attributes", {}) or {}).get(
                        self.fields["price_list_name"], ""
                    )
                    or ""
                ),
                price=str(
                    (row.get("attributes", {}) or {}).get(
                        self.fields["price_list_price"], ""
                    )
                    or ""
                ),
            )
            for row in rows
        ]

    async def add_nomenclature(
        self, application_id: int, command: AddNomenclatureRequest
    ) -> None:
        price_row = await self._request("GET", f"data91/{command.price_list_id}")
        attrs = (price_row.get("data", {}) or {}).get("attributes", {}) or {}
        name = str(attrs.get(self.fields["price_list_name"], "") or "")
        price = str(attrs.get(self.fields["price_list_price"], "0") or "0")
        try:
            total = str(float(price.replace(",", ".")) * command.quantity)
        except ValueError:
            total = "0"
        await self._request(
            "POST",
            "data351",
            json={
                "data": {
                    "type": "data351",
                    "attributes": {
                        self.fields["nomenclature_application_id"]: str(application_id),
                        self.fields["nomenclature_name"]: str(command.price_list_id),
                        self.fields["nomenclature_name_fallback"]: name,
                        self.fields["nomenclature_price"]: price,
                        self.fields["nomenclature_quantity"]: command.quantity,
                        self.fields["nomenclature_total"]: total,
                    },
                }
            },
        )

    async def delete_nomenclature(
        self, application_id: int, nomenclature_id: int
    ) -> None:
        row = await self._request("GET", f"data351/{nomenclature_id}")
        attrs = (row.get("data", {}) or {}).get("attributes", {}) or {}
        linked_application = str(
            attrs.get(self.fields["nomenclature_application_id"], "") or ""
        )
        if linked_application != str(application_id):
            raise ClientBaseError("Позиция не относится к этой заявке")
        await self._request("DELETE", f"data351/{nomenclature_id}")

    async def meter_catalog(self, query: str = "") -> list[MeterCatalogItem]:
        normalized_query = query.strip().casefold()
        registry_field = self.fields["meter_catalog_registry_number"]
        designation_field = self.fields["meter_catalog_designation"]
        rows = await self._list_all("data940", filter_expression="eq(status,0)")
        result: list[MeterCatalogItem] = []
        for row in rows:
            attrs = row.get("attributes", {}) or {}
            registry_number = str(attrs.get(registry_field, "") or "")
            designation = str(attrs.get(designation_field, "") or "")
            if normalized_query and normalized_query not in (
                f"{registry_number} {designation}".casefold()
            ):
                continue
            result.append(
                MeterCatalogItem(
                    id=int(row["id"]),
                    registry_number=registry_number,
                    designation=designation,
                )
            )
        return sorted(
            result,
            key=lambda item: (item.designation.casefold(), item.registry_number.casefold()),
        )

    async def add_meter(
        self, application_id: int, command: AddMeterRequest
    ) -> WaterMeter:
        application = await self._request("GET", f"data130/{application_id}")
        application_attrs = (
            (application.get("data", {}) or {}).get("attributes", {}) or {}
        )
        device_kind = "ИПУ ГВС" if "ГВС" in command.device_kind.upper() else "ИПУ ХВС"

        if not command.device_photo_filename or not command.device_photo_base64:
            raise ClientBaseError("Фото прибора обязательно")
        attrs = self._meter_attributes(application_attrs, application_id, command)
        response = await self._request(
            "POST",
            "data610",
            json={
                "data": {
                    "type": "data610",
                    "attributes": attrs,
                }
            },
        )
        row_id = int((response.get("data", {}) or {}).get("id", 0) or 0)
        if row_id <= 0:
            raise ClientBaseError("ClientBase не вернул ID добавленного ИПУ")
        return self._meter_from_values(row_id, command, device_kind)

    async def update_meter(
        self, application_id: int, meter_id: int, command: AddMeterRequest
    ) -> WaterMeter:
        existing = await self._request("GET", f"data610/{meter_id}")
        existing_attrs = (existing.get("data", {}) or {}).get("attributes", {}) or {}
        self._ensure_meter_application(existing_attrs, application_id)
        application = await self._request("GET", f"data130/{application_id}")
        application_attrs = (application.get("data", {}) or {}).get("attributes", {}) or {}
        attrs = self._meter_attributes(application_attrs, application_id, command)
        if not command.device_photo_base64:
            attrs.pop(self.fields["meter_device_photo"], None)
        if not command.passport_photo_base64:
            attrs.pop(self.fields["meter_passport_photo"], None)
        await self._request(
            "PATCH",
            f"data610/{meter_id}",
            json={"data": {"type": "data610", "id": str(meter_id), "attributes": attrs}},
        )
        device_kind = "ИПУ ГВС" if "ГВС" in command.device_kind.upper() else "ИПУ ХВС"
        return self._meter_from_values(meter_id, command, device_kind)

    async def delete_meter(self, application_id: int, meter_id: int) -> None:
        existing = await self._request("GET", f"data610/{meter_id}")
        attrs = (existing.get("data", {}) or {}).get("attributes", {}) or {}
        self._ensure_meter_application(attrs, application_id)
        await self._request("DELETE", f"data610/{meter_id}")

    def _ensure_meter_application(self, attrs: dict[str, Any], application_id: int) -> None:
        raw_value = attrs.get(self.fields["meter_application_id"], "")
        if str(application_id) not in re.findall(r"\d+", str(raw_value)):
            raise ClientBaseError("ИПУ не относится к этой заявке")

    def _meter_attributes(
        self,
        application_attrs: dict[str, Any],
        application_id: int,
        command: AddMeterRequest,
    ) -> dict[str, Any]:
        device_kind = "ИПУ ГВС" if "ГВС" in command.device_kind.upper() else "ИПУ ХВС"

        def crm_date(value: str) -> str:
            clean = value.strip()
            return f"{clean} 00:00:00" if re.fullmatch(r"\d{4}-\d{2}-\d{2}", clean) else clean

        attrs: dict[str, Any] = {
            self.fields["meter_address_id"]: application_attrs.get(self.fields["address_id"], ""),
            self.fields["meter_client_id"]: application_attrs.get(self.fields["client_id"], ""),
            self.fields["meter_application_id"]: str(application_id),
            self.fields["meter_device_kind"]: device_kind,
            self.fields["meter_type"]: command.meter_type,
            self.fields["meter_serial_number"]: command.serial_number,
            self.fields["meter_registry_number"]: command.registry_number,
            self.fields["meter_year"]: command.year,
            self.fields["meter_last_check"]: crm_date(command.last_check),
            self.fields["meter_next_check"]: crm_date(command.next_check),
            self.fields["meter_status"]: "Годен",
        }
        if command.device_photo_base64:
            attrs[self.fields["meter_device_photo"]] = [{
                "file_name": command.device_photo_filename,
                "content": command.device_photo_base64,
                "binary": True,
            }]
        if command.passport_photo_base64:
            attrs[self.fields["meter_passport_photo"]] = [{
                "file_name": command.passport_photo_filename,
                "content": command.passport_photo_base64,
                "binary": True,
            }]
        return attrs

    @staticmethod
    def _meter_from_values(
        meter_id: int, command: AddMeterRequest, device_kind: str
    ) -> WaterMeter:
        return WaterMeter(
            id=meter_id,
            device_kind=device_kind,
            meter_type=command.meter_type,
            serial_number=command.serial_number,
            registry_number=command.registry_number,
            year=command.year,
            last_check=command.last_check,
            next_check=command.next_check,
            status="Годен",
            device_photo=command.device_photo_filename,
            passport_photo=command.passport_photo_filename,
        )

    async def _application_photos(
        self, application_id: int, attrs: dict[str, Any]
    ) -> list[ApplicationPhoto]:
        photos: list[ApplicationPhoto] = []
        for mapping_key, title in PHOTO_FIELDS.items():
            field = self.fields[mapping_key]
            filenames = str(attrs.get(field, "") or "").splitlines()
            for filename in filter(None, (name.strip() for name in filenames)):
                photos.append(
                    ApplicationPhoto(field=field, title=title, filename=filename)
                )
        return photos

    async def photo_content(
        self, application_id: int, field: str, filename: str
    ) -> str:
        allowed_fields = [
            self.fields[key] for key in PHOTO_FIELDS if self.fields.get(key)
        ]
        if field not in allowed_fields:
            raise ClientBaseError("Недопустимое поле фотографии")
        candidates = [field] + [item for item in allowed_fields if item != field]
        last_not_found: ClientBaseError | None = None
        for candidate in candidates:
            try:
                return await self._download_photo(
                    application_id, candidate, filename
                )
            except ClientBaseError as exc:
                if exc.status_code != 404:
                    raise
                last_not_found = exc
        raise last_not_found or ClientBaseError("Файл фотографии отсутствует", 404)

    async def _download_photo(
        self, application_id: int, field: str, filename: str
    ) -> str:
        path = f"file/130/{field.lstrip('f')}/{application_id}/"
        try:
            async with httpx.AsyncClient(
                timeout=max(getattr(self, "timeout", 20.0), 60.0),
                follow_redirects=True,
                verify=False,
            ) as client:
                response = await client.get(
                    f"{self.base_url}/{path}",
                    headers=self.headers,
                    params={"filename": filename},
                )
            response.raise_for_status()
            return self._decode_file_response(response, filename)
        except httpx.HTTPStatusError as exc:
            raise ClientBaseError(
                f"Client Base вернул HTTP {exc.response.status_code}",
                status_code=exc.response.status_code,
            ) from exc
        except httpx.HTTPError as exc:
            raise ClientBaseError("Client Base не отдал файл") from exc
        except ValueError as exc:
            raise ClientBaseError("Client Base вернул неизвестный формат файла") from exc

    @classmethod
    def _decode_file_response(
        cls, response: httpx.Response, filename: str
    ) -> str:
        content_type = response.headers.get("content-type", "").lower()
        try:
            payload = response.json()
        except ValueError:
            if response.content and "text/html" not in content_type:
                return base64.b64encode(response.content).decode("ascii")
            raise ValueError("HTML response instead of file")
        return str(
            cls._extract_file_data(payload, filename).get("content", "") or ""
        )

    async def _nomenclature(self, application_id: int) -> list[NomenclatureItem]:
        relation_field = self.fields.get("nomenclature_application_id", "").strip()
        metadata: dict = {}
        if not relation_field:
            try:
                metadata = await self._request("GET", "table/351")
            except ClientBaseError:
                metadata = {}
            relation_field = self._find_field_id(metadata, ("заяв",))
        name_field = self._find_field_id(metadata, ("наименование", "прайс")) or self.fields["nomenclature_name"]
        price_field = self._find_field_id(metadata, ("цена",)) or self.fields["nomenclature_price"]
        quantity_field = (
            self._find_field_id(metadata, ("кол-во",))
            or self._find_field_id(metadata, ("количество",))
            or self.fields["nomenclature_quantity"]
        )
        total_field = self._find_field_id(metadata, ("сумма",)) or self.fields["nomenclature_total"]
        fallback_name_field = self.fields.get("nomenclature_name_fallback", "")
        rows: list[dict[str, Any]] = []
        if relation_field:
            try:
                payload = await self._request(
                    "GET",
                    "data351",
                    params={
                        "filter": f"and(eq(status,0),eq({relation_field},{application_id}))",
                        "page[limit]": 50,
                        "page[offset]": 0,
                    },
                )
            except ClientBaseError:
                payload = {"data": []}
            rows = payload.get("data", []) or []
        if not rows:
            # A subtable relation field is not returned by every ClientBase
            # version in table metadata. Probe the small set of actual fields
            # instead of downloading the entire table.
            sample = await self._request(
                "GET", "data351", params={"page[limit]": 1, "page[offset]": 0}
            )
            sample_rows = sample.get("data", []) or []
            known_fields = {name_field, price_field, quantity_field, total_field}
            candidate_fields = [
                field
                for field in (sample_rows[0].get("attributes", {}) if sample_rows else {})
                if field.startswith("f") and field not in known_fields
            ]
            for candidate in candidate_fields:
                try:
                    payload = await self._request(
                        "GET",
                        "data351",
                        params={
                            "filter": f"and(eq(status,0),eq({candidate},{application_id}))",
                            "page[limit]": 50,
                            "page[offset]": 0,
                        },
                    )
                except ClientBaseError:
                    continue
                candidate_rows = payload.get("data", []) or []
                if candidate_rows:
                    rows = candidate_rows
                    break
        raw_names = [
            str((row.get("attributes", {}) or {}).get(name_field, "") or "")
            for row in rows
        ]
        resolved_names = await asyncio.gather(
            *(self._price_list_name(raw_name) for raw_name in raw_names)
        )
        result: list[NomenclatureItem] = []
        for row, resolved_name in zip(rows, resolved_names):
            attrs = row.get("attributes", {}) or {}
            result.append(
                NomenclatureItem(
                    id=int(row["id"]),
                    name=str(
                        resolved_name
                        or attrs.get(fallback_name_field, "")
                        or ""
                    ),
                    price=str(attrs.get(price_field, "") or ""),
                    quantity=str(attrs.get(quantity_field, "") or ""),
                    total=str(attrs.get(total_field, "") or ""),
                )
            )
        return result

    async def _price_list_name(self, relation_value: str) -> str:
        ids = re.findall(r"\d+", relation_value)
        if not ids:
            return relation_value
        try:
            payload = await self._request("GET", f"data91/{ids[0]}")
        except ClientBaseError:
            return ""
        data = payload.get("data", {}) or {}
        attrs = data.get("attributes", {}) if isinstance(data, dict) else {}
        return str(
            attrs.get(self.fields.get("price_list_name", "f1158"), "") or ""
        )

    @staticmethod
    def _find_field_id(value: Any, keywords: tuple[str, ...]) -> str:
        if isinstance(value, dict):
            scalar_text = " ".join(
                str(item).lower()
                for item in value.values()
                if isinstance(item, (str, int, float))
            )
            field_ids = re.findall(r"\bf\d+\b", " ".join(map(str, value.keys())) + " " + scalar_text)
            raw_id = value.get("id")
            if not field_ids and str(raw_id).isdigit():
                field_ids = [f"f{raw_id}"]
            if field_ids and all(keyword in scalar_text for keyword in keywords):
                return field_ids[0]
            for nested in value.values():
                found = ClientBaseClient._find_field_id(nested, keywords)
                if found:
                    return found
        elif isinstance(value, list):
            for nested in value:
                found = ClientBaseClient._find_field_id(nested, keywords)
                if found:
                    return found
        return ""

    @staticmethod
    def _extract_file_data(payload: dict, filename: str = "") -> dict:
        data: Any = payload.get("data", payload)
        if isinstance(data, list):
            if filename:
                data = next(
                    (
                        item
                        for item in data
                        if str(
                            (item.get("attributes") or item).get(
                                "name", item.get("id", "")
                            )
                        )
                        == filename
                    ),
                    {},
                )
            else:
                data = data[0] if data else {}
        if isinstance(data, dict) and isinstance(data.get("attributes"), dict):
            data = data["attributes"]
        return data if isinstance(data, dict) else {}

    async def upload_photo(
        self,
        application_id: int,
        field: str,
        filename: str,
        content_base64: str,
    ) -> None:
        allowed_fields = {self.fields[key] for key in PHOTO_FIELDS}
        if field not in allowed_fields:
            raise ClientBaseError("Недопустимое поле фотографии")
        files = await self._file_payloads(application_id, field)
        files = [item for item in files if item["file_name"] != filename]
        files.append(
            {"file_name": filename, "content": content_base64, "binary": True}
        )
        await self._update_file_field(application_id, field, files)

    async def delete_photo(
        self, application_id: int, field: str, filename: str
    ) -> None:
        allowed_fields = {self.fields[key] for key in PHOTO_FIELDS}
        if field not in allowed_fields:
            raise ClientBaseError("Недопустимое поле фотографии")
        files = await self._file_payloads(
            application_id, field, exclude_filename=filename
        )
        await self._update_file_field(application_id, field, files)

    async def _file_payloads(
        self,
        application_id: int,
        field: str,
        exclude_filename: str = "",
    ) -> list[dict]:
        record = await self._request("GET", f"data130/{application_id}")
        data = record.get("data", {}) or {}
        attrs = data.get("attributes", {}) if isinstance(data, dict) else {}
        filenames = [
            name.strip()
            for name in str(attrs.get(field, "") or "").splitlines()
            if name.strip() and name.strip() != exclude_filename
        ]
        contents = await asyncio.gather(
            *(self.photo_content(application_id, field, name) for name in filenames)
        )
        return [
            {"file_name": name, "content": content, "binary": True}
            for name, content in zip(filenames, contents)
        ]

    async def _update_file_field(
        self, application_id: int, field: str, files: list[dict]
    ) -> None:
        await self._request(
            "PATCH",
            f"data130/{application_id}",
            json={
                "data": {
                    "type": "data130",
                    "id": str(application_id),
                    "attributes": {field: files},
                }
            },
        )

    async def _water_meters_by_address(self, address_id: Any) -> list[WaterMeter]:
        if address_id in (None, ""):
            return []
        relation_field = self.fields["meter_address_id"]
        payload = await self._request(
            "GET",
            "data610",
            params={
                "filter": f"and(eq(status,0),eq({relation_field},{address_id}))",
                "page[limit]": 50,
                "page[offset]": 0,
            },
        )
        result: list[WaterMeter] = []
        for row in payload.get("data", []) or []:
            attrs = row.get("attributes", {}) or {}
            result.append(
                WaterMeter(
                    id=int(row["id"]),
                    device_kind=str(attrs.get(self.fields["meter_device_kind"], "") or ""),
                    meter_type=str(attrs.get(self.fields["meter_type"], "") or ""),
                    modification=str(attrs.get(self.fields["meter_modification"], "") or ""),
                    accuracy_class=str(attrs.get(self.fields["meter_accuracy_class"], "") or ""),
                    serial_number=str(attrs.get(self.fields["meter_serial_number"], "") or ""),
                    registry_number=str(attrs.get(self.fields["meter_registry_number"], "") or ""),
                    year=str(attrs.get(self.fields["meter_year"], "") or ""),
                    last_check=str(attrs.get(self.fields["meter_last_check"], "") or ""),
                    next_check=str(attrs.get(self.fields["meter_next_check"], "") or ""),
                    status=str(attrs.get(self.fields["meter_status"], "") or ""),
                    reading=str(attrs.get(self.fields["meter_reading"], "") or ""),
                    device_photo=str(
                        attrs.get(self.fields["meter_device_photo"], "") or ""
                    ),
                    passport_photo=str(
                        attrs.get(self.fields["meter_passport_photo"], "") or ""
                    ),
                )
            )
        return result

    async def close_application(
        self, application_id: int, command: CloseApplicationRequest
    ) -> None:
        source = command.crm_attributes()
        attrs = {
            self.fields[key]: value
            for key, value in source.items()
            if key in self.fields
        }
        body = {
            "data": {
                "type": "data130",
                "id": str(application_id),
                "attributes": attrs,
            }
        }
        await self._request("PATCH", f"data130/{application_id}", json=body)

    async def send_to_rework(self, application_id: int) -> None:
        body = {
            "data": {
                "type": "data130",
                "id": str(application_id),
                "attributes": {self.fields["status"]: "На Доработку"},
            }
        }
        await self._request("PATCH", f"data130/{application_id}", json=body)

    async def _summary(self, item: dict) -> ApplicationSummary:
        attrs = item["attributes"]
        address = await self._related_value(
            "data470",
            attrs.get(self.fields["address_id"]),
            self.fields["address_value"],
        )
        client = await self._related_value(
            "data42",
            attrs.get(self.fields["client_id"]),
            self.fields["client_value"],
        )
        return ApplicationSummary(
            id=int(item["id"]),
            number=str(attrs.get(self.fields["number"], "")),
            work_date=attrs.get(self.fields["work_date"]),
            address=address,
            client=client,
            interval=str(attrs.get(self.fields["interval"], "") or ""),
            delivery_time=str(attrs.get(self.fields["delivery_time"], "") or ""),
            status=str(attrs.get(self.fields["status"], "")),
            phone_number=str(
                attrs.get(self.fields["phone_number"], "") or ""
            ),
            barrier=str(attrs.get(self.fields["barrier"], "") or ""),
            comments=str(attrs.get(self.fields["comments"], "") or ""),
        )

    async def _related_value(self, table: str, row_id: Any, field: str) -> str:
        if row_id in (None, ""):
            return ""
        payload = await self._request("GET", f"{table}/{row_id}")
        return str(payload.get("data", {}).get("attributes", {}).get(field, "") or "")
