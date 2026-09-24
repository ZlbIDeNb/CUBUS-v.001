import asyncio
import base64
import calendar
from datetime import date, datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
import json
import logging
from pathlib import Path
import re
import sqlite3
import time
from typing import Any

import httpx

from .config import Settings
from .models import (
    ApplicationDetails,
    ApplicationMapPoint,
    ApplicationPhoto,
    ApplicationStatusCount,
    ApplicationSummary,
    AddMeterRequest,
    AddNomenclatureRequest,
    CloseApplicationRequest,
    EmployeeEquipment,
    EmployeeProfile,
    HomeAddress,
    NomenclatureItem,
    MeterCatalogItem,
    MaterialUsageItem,
    PriceListItem,
    PeriodReport,
    ReportLine,
    ScheduleDay,
    WarehouseItem,
    WeatherSnapshot,
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

REWORK_REASON_FALLBACK = [
    "Не дозвон",
    "Не открыли дверь",
    "Не наша методика",
    "Нет денег",
    "Закажет позже",
    "Отказ от всего",
    "Нет горячей воды",
    "Течет кран",
    "Не подошел срок поверки",
    "Нет воды",
]

# Reference catalogs are persisted in SQLite and refreshed only on explicit request.
REFERENCE_CACHE_TTL_SECONDS = 10 * 365 * 24 * 60 * 60
DADATA_CACHE_TTL_SECONDS = 180 * 24 * 60 * 60
GEOCODE_MISS_TTL_SECONDS = 24 * 60 * 60

logger = logging.getLogger("uvicorn.error")


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
        self.cache_path = Path(__file__).resolve().parent.parent / "client_base_cache.sqlite3"
        self.dadata_api_key = (
            settings.dadata_api_key.get_secret_value() if settings.dadata_api_key else ""
        )
        self.dadata_secret_key = (
            settings.dadata_secret_key.get_secret_value() if settings.dadata_secret_key else ""
        )

    def _cache_get(self, key: str) -> Any | None:
        cache_path = getattr(self, "cache_path", None)
        if cache_path is None:
            return None
        try:
            with sqlite3.connect(cache_path) as connection:
                connection.execute(
                    "CREATE TABLE IF NOT EXISTS cache (key TEXT PRIMARY KEY, payload TEXT NOT NULL, expires REAL NOT NULL)"
                )
                row = connection.execute(
                    "SELECT payload, expires FROM cache WHERE key = ?", (key,)
                ).fetchone()
                if not row or float(row[1]) <= time.time():
                    if row:
                        connection.execute("DELETE FROM cache WHERE key = ?", (key,))
                    return None
                return json.loads(row[0])
        except (OSError, sqlite3.Error, ValueError, TypeError):
            return None

    def _cache_set(self, key: str, payload: Any, ttl_seconds: int) -> None:
        cache_path = getattr(self, "cache_path", None)
        if cache_path is None:
            return
        try:
            with sqlite3.connect(cache_path) as connection:
                connection.execute(
                    "CREATE TABLE IF NOT EXISTS cache (key TEXT PRIMARY KEY, payload TEXT NOT NULL, expires REAL NOT NULL)"
                )
                connection.execute(
                    "INSERT OR REPLACE INTO cache(key, payload, expires) VALUES (?, ?, ?)",
                    (key, json.dumps(payload, ensure_ascii=False), time.time() + ttl_seconds),
                )
        except (OSError, sqlite3.Error, ValueError, TypeError):
            pass

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
            detail = self._upstream_error_detail(exc.response)
            raise ClientBaseError(
                f"Client Base вернул HTTP {exc.response.status_code}"
                + (f": {detail}" if detail else ""),
                status_code=exc.response.status_code,
            ) from exc
        except (httpx.HTTPError, ValueError) as exc:
            raise ClientBaseError("Client Base не подтвердил операцию") from exc

    @staticmethod
    def _upstream_error_detail(response: httpx.Response) -> str:
        try:
            payload = response.json()
        except ValueError:
            logger.error(
                "Client Base HTTP %s response: %s",
                response.status_code,
                response.text[:1000],
            )
            return ""
        if isinstance(payload, dict):
            for key in ("detail", "message", "error", "errors"):
                value = payload.get(key)
                if value:
                    return str(value)[:500]
        return str(payload)[:500]

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
        applications = list(
            await asyncio.gather(*(self._summary(item) for item in application_rows))
        )
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

    async def period_report(
        self, crm_login: str, date_from: date, date_to: date
    ) -> PeriodReport:
        employee_id = await self._employee_id(crm_login)
        if employee_id is None:
            return PeriodReport(date_from=date_from, date_to=date_to)
        conditions = [
            "eq(status,0)",
            f"eq({self.fields['application_metrolog_id']},{employee_id})",
            f"eq({self.fields['status']},'Выполнено')",
            f"gte({self.fields['work_date']},'{date_from.isoformat()} 00:00:00')",
            f"lte({self.fields['work_date']},'{date_to.isoformat()} 23:59:59')",
        ]
        rows = await self._list_all(
            "data130", filter_expression=f"and({','.join(conditions)})"
        )

        def amount(value: Any) -> Decimal:
            try:
                return Decimal(str(value or "0").replace(" ", "").replace(",", "."))
            except InvalidOperation:
                return Decimal("0")

        cash = sum(
            (amount((row.get("attributes", {}) or {}).get(self.fields["cash_sum"])) for row in rows),
            Decimal("0"),
        )
        card = sum(
            (amount((row.get("attributes", {}) or {}).get(self.fields["card_sum"])) for row in rows),
            Decimal("0"),
        )
        def normalized(value: str) -> str:
            return re.sub(r"[^0-9a-zа-я]+", " ", value.casefold().replace("ё", "е")).strip()

        price_items = await self.price_list()
        kinds = {normalized(item.name): item.item_kind for item in price_items}

        material_prefixes = (
            "счетчик", "кран 1 2", "тройник", "муфта", "нип", "угол",
            "футор", "цанга", "удлинитель", "аэратор", "гибкая подводка",
            "проволока", "прокладка", "лен", "труба", "обратный клапан",
            "фильтр грубой очистки", "присоединитель с обратным клапаном",
        )

        def is_material(name: str) -> bool:
            clean = normalized(name)
            if clean.startswith(material_prefixes):
                return True
            configured = kinds.get(clean)
            if configured in {"material", "service"}:
                return configured == "material"
            return False

        # ClientBase table "Связь товаров и услуг": a completed service consumes
        # the linked stock item in the same quantity. Keep the matching tolerant
        # to abbreviated service names used in older applications.
        def linked_material(name: str) -> str | None:
            clean = normalized(name)
            if "замена обратного клапана" in clean:
                return "Обратный клапан (Таблетка)"
            if "замена фильтра грубой очистки" in clean:
                return "Фильтр грубой очистки"
            if "замена присоединителя" in clean and "valtec" in clean:
                return "Присоединитель с обратным клапаном (Valtec)"
            if "замена присоединителя" in clean and "латун" in clean:
                return "Присоединитель с обратным клапаном (Латунь)"
            if "замена шарового крана" in clean or "замена крана" in clean:
                return "Кран 1/2"
            if "ителма" in clean and "универсаль" in clean:
                return "Счетчик ХВС/ГВС УНИВЕРСАЛЬНЫЙ.D080 (Импульс)"
            if "ителма" in clean and "гвс" in clean:
                return "Счетчик ГВС ITELMA WFW24.D080 (Импульс)"
            if "ителма" in clean and "хвс" in clean:
                return "Счетчик ХВС ITELMA WFK24.D080 (Импульс)"
            if "эконом" in clean and "100" in clean:
                return "Счетчик ЭКО НОМ СВ 15-100"
            if "эконом" in clean and "80" in clean:
                return "Счетчик ЭКО НОМ СВ 15-80"
            return None

        # Fixed tariff and payout rules supplied by the business owner.
        # Tuple: unit price, metrologist gross, company base.
        def service_rule(name: str, fallback_total: Decimal, quantity: Decimal) -> tuple[Decimal, Decimal, Decimal]:
            clean = normalized(name)
            if "поверк" in clean and "800" in clean:
                return Decimal("800"), Decimal("200"), Decimal("600")
            if "поверк" in clean and "550" in clean:
                return Decimal("550"), Decimal("200"), Decimal("350")
            if "эконом" in clean and ("замен" in clean or "комплекс" in clean):
                return Decimal("5300"), Decimal("1950"), Decimal("3350")
            if "ителма" in clean and ("замен" in clean or "комплекс" in clean):
                return Decimal("6300"), Decimal("2150"), Decimal("4150")
            fixed_prices = (
                (("монтаж ипу", "представлен"), Decimal("4300")),
                (("замена фильтра грубой очистки",), Decimal("2800")),
                (("замена присоединителя", "латун"), Decimal("2800")),
                (("замена уплотнительных прокладок",), Decimal("1100")),
                (("замена шарового крана",), Decimal("2200")),
                (("замена крана",), Decimal("2200")),
                (("очистка фильтра грубой очистки",), Decimal("1700")),
                (("устранение течи",), Decimal("1500")),
                (("фиксация показаний",), Decimal("800")),
                (("замена обратного клапана",), Decimal("1700")),
                (("выезд специалиста",), Decimal("900")),
                (("затрудненного доступа",), Decimal("1000")),
                (("сложный доступ",), Decimal("1000")),
                (("дополнительные сантехнические работы",), Decimal("1000")),
                (("замена присоединителя", "valtec"), Decimal("3000")),
            )
            for keywords, price in fixed_prices:
                if all(keyword in clean for keyword in keywords):
                    half = price / 2
                    return price, half, half
            unit_price = fallback_total / quantity if quantity else Decimal("0")
            half = unit_price / 2
            return unit_price, half, half

        positions = await asyncio.gather(
            *(self._nomenclature(int(row["id"])) for row in rows)
        )
        service_totals: dict[str, tuple[Decimal, Decimal]] = {}
        material_totals: dict[str, tuple[Decimal, Decimal]] = {}
        for application_positions in positions:
            for item in application_positions:
                target = material_totals if is_material(item.name) else service_totals
                old_quantity, old_total = target.get(
                    item.name, (Decimal("0"), Decimal("0"))
                )
                target[item.name] = (
                    old_quantity + amount(item.quantity),
                    old_total + amount(item.total),
                )

        # Add stock consumption implied by services. When ClientBase also sends
        # the product as a separate nomenclature row, use the greater quantity
        # instead of adding it again, so one sale cannot create a double write-off.
        linked_totals: dict[str, Decimal] = {}
        for service_name, (quantity, _) in service_totals.items():
            product_name = linked_material(service_name)
            if product_name:
                linked_totals[product_name] = (
                    linked_totals.get(product_name, Decimal("0")) + quantity
                )

        def material_identity(value: str) -> str:
            return normalized(value).replace(" ", "")

        for product_name, linked_quantity in linked_totals.items():
            existing_name = next(
                (
                    name
                    for name in material_totals
                    if material_identity(name) == material_identity(product_name)
                ),
                None,
            )
            if existing_name is None:
                material_totals[product_name] = (linked_quantity, Decimal("0"))
            else:
                existing_quantity, existing_total = material_totals[existing_name]
                material_totals[existing_name] = (
                    max(existing_quantity, linked_quantity),
                    existing_total,
                )

        service_drafts: list[tuple[str, Decimal, Decimal, Decimal, Decimal]] = []
        for name, (quantity, raw_total) in sorted(
            service_totals.items(), key=lambda item: item[0].casefold()
        ):
            unit_price, metrologist_unit, company_unit = service_rule(
                name, raw_total, quantity
            )
            service_drafts.append((
                name,
                quantity,
                unit_price,
                metrologist_unit * quantity,
                company_unit * quantity,
            ))

        total_commission = (card * Decimal("0.05")).quantize(
            Decimal("0.01"), rounding=ROUND_HALF_UP
        )
        saved_administrative_expenses = self._cache_get(
            f"profile-administrative-expenses:v1:{crm_login.casefold()}"
        )
        administrative_expenses_status = (
            saved_administrative_expenses
            if saved_administrative_expenses in {"Да", "Нет"}
            else "Нет"
        )
        total_metrologist_gross = sum(
            (gross for _, _, _, gross, _ in service_drafts), Decimal("0")
        )
        administrative_expenses = (
            total_metrologist_gross * Decimal("0.15")
            if administrative_expenses_status == "Да"
            else Decimal("0")
        ).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        service_revenue = sum(
            (unit_price * quantity for _, quantity, unit_price, _, _ in service_drafts),
            Decimal("0"),
        )
        services: list[ReportLine] = []
        assigned_commission = Decimal("0")
        assigned_administrative_expenses = Decimal("0")
        for index, (name, quantity, unit_price, metrologist_gross, company_base) in enumerate(service_drafts):
            if index == len(service_drafts) - 1:
                commission = total_commission - assigned_commission
            elif service_revenue:
                commission = (total_commission * unit_price * quantity / service_revenue).quantize(
                    Decimal("0.01"), rounding=ROUND_HALF_UP
                )
                assigned_commission += commission
            else:
                commission = Decimal("0")
            if index == len(service_drafts) - 1:
                line_administrative_expenses = (
                    administrative_expenses - assigned_administrative_expenses
                )
            elif total_metrologist_gross:
                line_administrative_expenses = (
                    administrative_expenses
                    * metrologist_gross
                    / total_metrologist_gross
                ).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                assigned_administrative_expenses += line_administrative_expenses
            else:
                line_administrative_expenses = Decimal("0")
            services.append(ReportLine(
                name=name,
                quantity=quantity,
                unit_price=unit_price,
                total=unit_price * quantity,
                metrologist_gross=metrologist_gross,
                bank_commission=commission,
                administrative_expenses=line_administrative_expenses,
                metrologist_net=(
                    metrologist_gross - commission - line_administrative_expenses
                ),
                company=company_base + commission + line_administrative_expenses,
            ))

        materials = [
            ReportLine(name=name, quantity=quantity, total=total)
            for name, (quantity, total) in sorted(
                material_totals.items(), key=lambda item: item[0].casefold()
            )
        ]
        metrologist_gross = sum((line.metrologist_gross for line in services), Decimal("0"))
        metrologist_net = (
            metrologist_gross - total_commission - administrative_expenses
        )
        company = sum((line.company for line in services), Decimal("0"))

        return PeriodReport(
            date_from=date_from,
            date_to=date_to,
            applications_count=len(rows),
            total=cash + card,
            cash=cash,
            card=card,
            bank_commission=total_commission,
            administrative_expenses=administrative_expenses,
            administrative_expenses_status=administrative_expenses_status,
            metrologist_gross=metrologist_gross,
            metrologist_net=metrologist_net,
            company=company,
            services=services,
            materials=materials,
        )

    async def application_map_points(
        self, crm_login: str, work_date: date | None = None
    ) -> list[ApplicationMapPoint]:
        applications = await self.list_applications(crm_login, work_date=work_date)
        resolved_addresses: dict[str, dict[str, Any] | None] = {}
        points: list[ApplicationMapPoint] = []
        for application in applications:
            address = application.address.strip()
            if not address:
                continue
            normalized_address = address.casefold()
            if normalized_address not in resolved_addresses:
                resolved_addresses[normalized_address] = await self._dadata_address(address)
            resolved = resolved_addresses[normalized_address]
            if resolved is None:
                continue
            points.append(
                ApplicationMapPoint(
                    application_id=application.id,
                    number=application.number,
                    address=str(resolved["display_address"]),
                    interval=application.interval,
                    delivery_time=application.delivery_time,
                    phone_number=application.phone_number,
                    client=application.client,
                    comments=application.comments,
                    latitude=float(resolved["latitude"]),
                    longitude=float(resolved["longitude"]),
                )
            )
        return points

    async def _dadata_address(self, address: str) -> dict[str, Any] | None:
        query = self._compact_address(address)
        cache_key = f"geocode:dadata:v1:{query.casefold()}"
        cached = self._cache_get(cache_key)
        if isinstance(cached, dict):
            return cached or None
        if not self.dadata_api_key or not self.dadata_secret_key:
            raise ClientBaseError(
                "Не настроены DADATA_API_KEY и DADATA_SECRET_KEY на сервере"
            )
        try:
            async with httpx.AsyncClient(timeout=self.timeout, follow_redirects=True) as client:
                response = await client.post(
                    "https://cleaner.dadata.ru/api/v1/clean/address",
                    headers={
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                        "Authorization": f"Token {self.dadata_api_key}",
                        "X-Secret": self.dadata_secret_key,
                    },
                    json=[query],
                )
            response.raise_for_status()
            payload = response.json()
            item = payload[0] if isinstance(payload, list) and payload else {}
            latitude = item.get("geo_lat")
            longitude = item.get("geo_lon")
            if latitude in (None, "") or longitude in (None, ""):
                self._cache_set(cache_key, {}, GEOCODE_MISS_TTL_SECONDS)
                return None
            resolved = {
                "full_address": str(item.get("result") or query),
                "display_address": self._dadata_street_and_house(item, query),
                "latitude": float(latitude),
                "longitude": float(longitude),
            }
            self._cache_set(cache_key, resolved, DADATA_CACHE_TTL_SECONDS)
            return resolved
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 403:
                raise ClientBaseError(
                    "DaData отклонила запрос: подтвердите почту аккаунта DaData "
                    "и проверьте баланс сервиса",
                    status_code=403,
                ) from exc
            raise ClientBaseError(
                f"DaData вернула HTTP {exc.response.status_code}",
                status_code=exc.response.status_code,
            ) from exc
        except (httpx.HTTPError, ValueError, TypeError, KeyError) as exc:
            raise ClientBaseError("Не удалось получить адрес и координаты через DaData") from exc

    @staticmethod
    def _compact_address(address: str) -> str:
        without_area = re.sub(r"^\s*\([^)]*\)\s*", "", address).strip()
        without_apartment = re.sub(
            r",?\s*(?:кв(?:артира)?\.?|пом(?:ещение)?\.?)\s*[^,]*$",
            "",
            without_area,
            flags=re.IGNORECASE,
        ).strip(" ,")
        street_and_house = without_apartment or without_area or address.strip()
        has_explicit_city = bool(
            re.search(
                r"(?:^|,\s*)(?:г\.?\s+|москва\b|санкт-петербург\b)",
                street_and_house,
                flags=re.IGNORECASE,
            )
        )
        first_part = street_and_house.split(",", 1)[0]
        if not has_explicit_city and (
            "," not in street_and_house
            or re.match(
                r"^(?:ул\.?|улица|проспект|пр-т|пер\.?|переулок|ш\.?|шоссе)\s+",
                first_part,
                flags=re.IGNORECASE,
            )
        ):
            return f"Москва, {street_and_house}"
        return street_and_house

    @staticmethod
    def _dadata_street_and_house(item: dict[str, Any], fallback: str) -> str:
        parts = [
            str(item.get("street_with_type") or "").strip(),
            " ".join(
                filter(
                    None,
                    [
                        str(item.get("house_type") or "").strip(),
                        str(item.get("house") or "").strip(),
                    ],
                )
            ),
            " ".join(
                filter(
                    None,
                    [
                        str(item.get("block_type") or "").strip(),
                        str(item.get("block") or "").strip(),
                    ],
                )
            ),
        ]
        return ", ".join(part for part in parts if part) or fallback

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

    async def _user_id(self, crm_login: str) -> int | None:
        safe_login = crm_login.replace("'", "\\'")
        payload = await self._request(
            "GET",
            "user",
            params={
                "filter": f"and(eq(arc,0),eq(login,'{safe_login}'))",
                "page[limit]": 1,
                "page[offset]": 0,
            },
        )
        rows = payload.get("data", []) or []
        if not rows:
            return None
        try:
            return int(rows[0]["id"])
        except (KeyError, TypeError, ValueError):
            return None

    async def metrolog_warehouse(
        self, crm_login: str, force_refresh: bool = False
    ) -> list[WarehouseItem]:
        cache_key = f"metrolog-warehouse:v1:{crm_login.casefold()}"
        cached = None if force_refresh else self._cache_get(cache_key)
        if cached is not None:
            return [WarehouseItem(**item) for item in cached]
        try:
            user_id = await self._user_id(crm_login)
        except ClientBaseError:
            user_id = None
        rows: list[dict] = []
        if user_id is not None:
            rows = await self._list_all(
                "data760",
                filter_expression=(
                    f"and(eq(status,0),"
                    f"eq({self.fields['warehouse_user']},{user_id}))"
                ),
            )
        # Older ClientBase configurations may not grant API access to /user.
        # In that case the relation field f13020 provides the same metrolog link.
        if not rows:
            employee = await self._employee_row(crm_login)
            if employee is None:
                return []
            employee_ids = {
                str(employee.get("id", "") or ""),
                str(
                    (employee.get("attributes", {}) or {}).get(
                        self.fields["employee_id"], ""
                    )
                    or ""
                ),
            } - {""}
            if not employee_ids:
                return []
            relation_conditions = ",".join(
                f"eq({self.fields['warehouse_employee']},{employee_id})"
                for employee_id in sorted(employee_ids)
            )
            relation_filter = (
                relation_conditions
                if len(employee_ids) == 1
                else f"or({relation_conditions})"
            )
            rows = await self._list_all(
                "data760",
                filter_expression=f"and(eq(status,0),{relation_filter})",
            )
        raw_names = [
            str(
                (row.get("attributes", {}) or {}).get(
                    self.fields["warehouse_name"], ""
                )
                or ""
            )
            for row in rows
        ]
        resolved_names = await asyncio.gather(
            *(self._price_list_name(name) for name in raw_names)
        )
        result: list[WarehouseItem] = []
        for row, resolved_name, raw_name in zip(rows, resolved_names, raw_names):
            attrs = row.get("attributes", {}) or {}
            result.append(
                WarehouseItem(
                    id=int(row["id"]),
                    name=resolved_name or raw_name,
                    incoming=str(attrs.get(self.fields["warehouse_incoming"], "") or ""),
                    outgoing=str(attrs.get(self.fields["warehouse_outgoing"], "") or ""),
                    balance=str(attrs.get(self.fields["warehouse_balance"], "") or ""),
                    written_off_to_warehouse=str(
                        attrs.get(self.fields["warehouse_written_off"], "") or ""
                    ),
                    defect_quantity=str(
                        attrs.get(self.fields["warehouse_defect_quantity"], "") or ""
                    ),
                    defect_position=str(
                        attrs.get(self.fields["warehouse_defect_position"], "") or ""
                    ),
                    writeoff_goods_quantity=str(
                        attrs.get(self.fields["warehouse_writeoff_goods"], "") or ""
                    ),
                    service_writeoff_quantity=str(
                        attrs.get(self.fields["warehouse_service_writeoff"], "") or ""
                    ),
                    total_written_off=str(
                        attrs.get(self.fields["warehouse_total_written_off"], "") or ""
                    ),
                )
            )
        result = sorted(result, key=lambda item: item.name.casefold())
        self._cache_set(
            cache_key,
            [item.model_dump() for item in result],
            365 * 24 * 60 * 60,
        )
        return result

    async def employee_profile(
        self, crm_login: str, role: str, device_name: str
    ) -> EmployeeProfile:
        row = await self._employee_row(crm_login)
        attrs = row.get("attributes", {}) if row else {}
        home = self._cache_get(f"profile-home:v1:{crm_login.casefold()}")
        home = home if isinstance(home, dict) else {}
        saved_administrative_expenses = self._cache_get(
            f"profile-administrative-expenses:v1:{crm_login.casefold()}"
        )
        administrative_expenses = (
            saved_administrative_expenses
            if saved_administrative_expenses in {"Да", "Нет"}
            else "Нет"
        )
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
            home_address=str(home.get("address", "") or ""),
            home_latitude=home.get("latitude"),
            home_longitude=home.get("longitude"),
            administrative_expenses=administrative_expenses,
            equipment=self._employee_equipment(attrs),
        )

    def save_administrative_expenses(self, crm_login: str, value: str) -> None:
        if value not in {"Да", "Нет"}:
            raise ClientBaseError("Неизвестное значение административных расходов")
        self._cache_set(
            f"profile-administrative-expenses:v1:{crm_login.casefold()}",
            value,
            REFERENCE_CACHE_TTL_SECONDS,
        )

    async def save_home_address(self, crm_login: str, address: str) -> HomeAddress:
        resolved = await self._dadata_address(address)
        if resolved is None:
            raise ClientBaseError("DaData не нашла указанный дом")
        home = HomeAddress(
            address=str(resolved["full_address"]),
            latitude=float(resolved["latitude"]),
            longitude=float(resolved["longitude"]),
        )
        self._cache_set(
            f"profile-home:v1:{crm_login.casefold()}",
            home.model_dump(),
            REFERENCE_CACHE_TTL_SECONDS,
        )
        return home

    async def current_weather(self, crm_login: str) -> WeatherSnapshot:
        # Until device geolocation is introduced, the dashboard is fixed to Moscow.
        latitude = 55.7558
        longitude = 37.6173
        city = "г. Москва"
        cache_key = f"weather-current:v1:{latitude:.3f}:{longitude:.3f}"
        cached = self._cache_get(cache_key)
        if isinstance(cached, dict):
            return WeatherSnapshot(**cached)
        try:
            async with httpx.AsyncClient(timeout=12, follow_redirects=True) as client:
                response = await client.get(
                    "https://api.open-meteo.com/v1/forecast",
                    params={
                        "latitude": latitude,
                        "longitude": longitude,
                        "current": (
                            "temperature_2m,relative_humidity_2m,surface_pressure"
                        ),
                        "timezone": "Europe/Moscow",
                    },
                )
                response.raise_for_status()
                current = (response.json() or {}).get("current", {}) or {}
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise ClientBaseError("Погода временно недоступна") from exc
        pressure_hpa = current.get("surface_pressure")
        weather = WeatherSnapshot(
            city=city,
            temperature=current.get("temperature_2m"),
            humidity=current.get("relative_humidity_2m"),
            pressure_mm_hg=(
                round(float(pressure_hpa) * 0.750062, 1)
                if pressure_hpa is not None
                else None
            ),
        )
        self._cache_set(cache_key, weather.model_dump(), 15 * 60)
        return weather

    async def employee_schedule(
        self, crm_login: str, year: int, month: int, refresh: bool = False
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
            # Do not paint an assumed Mon-Fri pattern as a real schedule.
            # Days become green/red only when table 220 contains a record.
            result.append(
                ScheduleDay(
                    date=current,
                    day=day_number,
                    is_working=False,
                    has_record=False,
                )
            )
        # Table 220 is the only authoritative source for per-day status.
        try:
            async with asyncio.timeout(12):
                result = await self._overlay_schedule_from_table_220(
                    row, result, year, month, refresh=refresh
                )
        except (ClientBaseError, KeyError, TypeError, ValueError, TimeoutError):
            # A temporary ClientBase failure must not hide the whole calendar.
            pass
        return result

    async def _overlay_schedule_from_table_220(
        self,
        employee_row: dict | None,
        result: list[ScheduleDay],
        year: int,
        month: int,
        refresh: bool = False,
    ) -> list[ScheduleDay]:
        """Apply the exact table-220 fields used by ClientBase report 391."""
        employee_record_id = (employee_row or {}).get("id")
        if employee_record_id in (None, ""):
            return result

        employee_field = self.fields["schedule_employee"]
        date_field = self.fields["schedule_date"]
        status_field = self.fields["schedule_status"]
        _, last_day = calendar.monthrange(year, month)
        first = date(year, month, 1).isoformat()
        last = date(year, month, last_day).isoformat()
        cache_key = (
            f"schedule220-record:{employee_record_id}:{year:04d}-{month:02d}"
        )
        schedule_rows = None if refresh else self._cache_get(cache_key)
        if schedule_rows is None:
            schedule_rows = await self._list_all(
                "data220",
                filter_expression=(
                    f"and(eq(status,0),eq({employee_field},{employee_record_id}),"
                    f"gte({date_field},'{first} 00:00:00'),"
                    f"lte({date_field},'{last} 23:59:59'))"
                ),
            )
            self._cache_set(cache_key, schedule_rows, 60)

        overrides: dict[date, str] = {}
        for schedule_row in schedule_rows:
            values = schedule_row.get("attributes", {}) or {}
            work_date = self._parse_schedule_date(values.get(date_field, ""))
            if work_date is None:
                continue
            raw_status = str(values.get(status_field, "") or "").strip()
            if raw_status in {"Работает", "Выходной", "Отпуск"}:
                overrides[work_date] = raw_status

        logger.info(
            "Schedule table 220: employee_record=%s rows=%s matched_days=%s",
            employee_record_id,
            len(schedule_rows),
            len(overrides),
        )
        return [
            item.model_copy(
                update={
                    "is_working": overrides[item.date] == "Работает",
                    "has_record": True,
                    "work_status": overrides[item.date],
                }
            )
            if item.date in overrides else item
            for item in result
        ]

    @staticmethod
    def _parse_schedule_date(value: Any) -> date | None:
        raw = str(value or "").strip()
        for pattern, order in (
            (r"(\d{4})-(\d{2})-(\d{2})", "ymd"),
            (r"(\d{2})\.(\d{2})\.(\d{4})", "dmy"),
        ):
            match = re.search(pattern, raw)
            if not match:
                continue
            parts = tuple(map(int, match.groups()))
            try:
                return date(*parts) if order == "ymd" else date(parts[2], parts[1], parts[0])
            except ValueError:
                return None
        return None


    async def material_usage(
        self, crm_login: str, work_date: date
    ) -> list[MaterialUsageItem]:
        cache_key = f"material-usage:{crm_login}:{work_date.isoformat()}"
        cached = self._cache_get(cache_key)
        if cached is not None:
            return [MaterialUsageItem(**item) for item in cached]
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
        result = [
            MaterialUsageItem(name=name, quantity=str(quantity), total=str(total))
            for name, (quantity, total) in sorted(totals.items(), key=lambda item: item[0].casefold())
        ]
        self._cache_set(cache_key, [item.model_dump() for item in result], 60)
        return result

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

    async def price_list(self, force_refresh: bool = False) -> list[PriceListItem]:
        cache_key = "catalog:price-list:v4"
        cached = None if force_refresh else self._cache_get(cache_key)
        if cached is not None:
            return [PriceListItem(**item) for item in cached]
        rows = await self._list_all("data91")
        result: list[PriceListItem] = []
        for row in rows:
            attrs = row.get("attributes", {}) or {}
            category = self._catalog_filter_text(
                attrs.get(self.fields["price_list_category"], "")
            )
            legacy_type = self._catalog_filter_text(
                attrs.get(self.fields.get("price_list_type", "f2900"), "")
            )
            if "услуг" not in category and "товар" not in category:
                category = legacy_type
            warehouse = self._catalog_filter_text(
                attrs.get(self.fields["price_list_warehouse"], "")
            )
            bot_enabled = self._catalog_filter_text(
                attrs.get(self.fields["price_list_bot_enabled"], "")
            )
            # f1157 is a relation and the API returns its internal record id.
            # In this table the requested split is represented directly by f13291:
            # no warehouse stock means a service, warehouse stock means a material.
            category_is_service = "услуг" in category
            category_is_material = "товар" in category
            category_is_unknown = not category_is_service and not category_is_material
            is_service = self._is_no(warehouse) and (
                category_is_unknown or category_is_service
            )
            is_material = self._is_yes(warehouse) and (
                category_is_unknown or category_is_material
            )
            if not self._is_yes(bot_enabled) or not (is_service or is_material):
                continue
            result.append(
                PriceListItem(
                    id=int(row["id"]),
                    name=str(attrs.get(self.fields["price_list_name"], "") or ""),
                    price=str(attrs.get(self.fields["price_list_price"], "") or ""),
                    item_kind="service" if is_service else "material",
                )
            )
        result.sort(key=lambda item: item.name.casefold())
        logger.info(
            "Price list table 91: rows=%s eligible=%s",
            len(rows),
            len(result),
        )
        self._cache_set(
            cache_key,
            [item.model_dump() for item in result],
            REFERENCE_CACHE_TTL_SECONDS,
        )
        return result

    @staticmethod
    def _catalog_filter_text(value: Any) -> str:
        if isinstance(value, (dict, list)):
            return json.dumps(value, ensure_ascii=False).casefold()
        return str(value or "").strip().casefold()

    @staticmethod
    def _is_yes(value: str) -> bool:
        return value in {"1", "true", "yes", "да"} or bool(
            re.search(r"(?:^|\W)(?:да|true|yes|1)(?:$|\W)", value)
        )

    @staticmethod
    def _is_no(value: str) -> bool:
        return value in {"0", "false", "no", "нет"} or bool(
            re.search(r"(?:^|\W)(?:нет|false|no|0)(?:$|\W)", value)
        )

    async def add_nomenclature(
        self, application_id: int, command: AddNomenclatureRequest
    ) -> None:
        price_row = await self._request("GET", f"data91/{command.price_list_id}")
        attrs = (price_row.get("data", {}) or {}).get("attributes", {}) or {}
        name = str(attrs.get(self.fields["price_list_name"], "") or "")
        price = str(attrs.get(self.fields["price_list_price"], "0") or "0")
        if command.total is not None:
            total_decimal = command.total
            price = str(total_decimal / command.quantity)
            total = str(total_decimal)
        else:
            try:
                total = str(float(price.replace(" ", "").replace(",", ".")) * command.quantity)
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

    async def meter_catalog(
        self, query: str = "", force_refresh: bool = False
    ) -> list[MeterCatalogItem]:
        normalized_query = query.strip().casefold()
        registry_field = self.fields["meter_catalog_registry_number"]
        designation_field = self.fields["meter_catalog_designation"]
        cache_key = "catalog:meters"
        rows = None if force_refresh else self._cache_get(cache_key)
        if rows is None:
            rows = await self._list_all("data940", filter_expression="eq(status,0)")
            self._cache_set(cache_key, rows, REFERENCE_CACHE_TTL_SECONDS)
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
        date_fields = {
            field: attrs.pop(field)
            for field in (
                self.fields.get("meter_last_check"),
                self.fields.get("meter_next_check"),
                self.fields.get("meter_form_last_check"),
                self.fields.get("meter_form_next_check"),
            )
            if field and field in attrs
        }
        all_date_fields = [
            field
            for field in (
                self.fields.get("meter_last_check"),
                self.fields.get("meter_next_check"),
                self.fields.get("meter_form_last_check"),
                self.fields.get("meter_form_next_check"),
            )
            if field
        ]
        temporary_date = next(
            iter(date_fields.values()),
            f"{date.today().isoformat()} 00:00:00",
        )
        # ClientBase API 2.0 crashes while serializing a newly created row when
        # one of its date fields is empty. Supply temporary valid dates for the
        # POST, then clear the fields that must be empty for the selected status.
        fields_to_clear = [field for field in all_date_fields if field not in date_fields]
        for field in all_date_fields:
            attrs[field] = date_fields.get(field, temporary_date)
        file_fields = {
            field: attrs.pop(field)
            for field in (
                self.fields.get("meter_device_photo"),
                self.fields.get("meter_passport_photo"),
            )
            if field and field in attrs
        }
        try:
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
        except ClientBaseError as exc:
            raise ClientBaseError(
                f"Не удалось создать основную запись ИПУ: {exc}",
                exc.status_code,
            ) from exc
        row_id = int((response.get("data", {}) or {}).get("id", 0) or 0)
        if row_id <= 0:
            raise ClientBaseError("ClientBase не вернул ID добавленного ИПУ")
        try:
            for field in fields_to_clear:
                await self._clear_meter_date(row_id, field)
            if file_fields:
                await self._request(
                    "PATCH",
                    f"data610/{row_id}",
                    json={
                        "data": {
                            "type": "data610",
                            "id": str(row_id),
                            "attributes": file_fields,
                        }
                    },
                )
        except ClientBaseError as exc:
            try:
                await self._request("DELETE", f"data610/{row_id}")
            except ClientBaseError:
                logger.exception(
                    "Could not roll back meter %s after photo upload failure",
                    row_id,
                )
            raise ClientBaseError(
                f"ИПУ не сохранён, ошибка дополнительного поля: {exc}",
                exc.status_code,
            ) from exc
        return self._meter_from_values(row_id, command, device_kind)

    async def _clear_meter_date(self, meter_id: int, field: str) -> None:
        try:
            await self._request(
                "PATCH",
                f"data610/{meter_id}",
                json={
                    "data": {
                        "type": "data610",
                        "id": str(meter_id),
                        "attributes": {field: None},
                    }
                },
            )
        except ClientBaseError as exc:
            # Some ClientBase revisions commit the empty date and then fail only
            # while formatting that empty value for the response.
            if "format() on bool" in str(exc):
                logger.info(
                    "ClientBase cleared date field %s on meter %s but failed to format the response",
                    field,
                    meter_id,
                )
                return
            raise ClientBaseError(
                f"не удалось очистить поле даты {field}: {exc}",
                exc.status_code,
            ) from exc

    async def _patch_meter_date(self, meter_id: int, field: str, value: str) -> None:
        candidates = self._client_base_date_candidates(value)
        last_error: ClientBaseError | None = None
        for candidate in candidates:
            try:
                await self._request(
                    "PATCH",
                    f"data610/{meter_id}",
                    json={
                        "data": {
                            "type": "data610",
                            "id": str(meter_id),
                            "attributes": {field: candidate},
                        }
                    },
                )
                return
            except ClientBaseError as exc:
                last_error = exc
                if "format() on bool" not in str(exc):
                    break
        raise ClientBaseError(
            f"поле {field}, значение даты не принято КБ ({', '.join(candidates)}): {last_error}",
            last_error.status_code if last_error else None,
        )

    @staticmethod
    def _client_base_date_candidates(value: str) -> list[str]:
        source = value.strip()
        raw_date = source.split(" ", 1)[0]
        try:
            parsed = datetime.strptime(raw_date, "%Y-%m-%d")
        except ValueError:
            return [source]
        candidates = [
            parsed.strftime("%Y-%m-%d %H:%M:%S"),
            parsed.strftime("%Y-%m-%d"),
            parsed.strftime("%d.%m.%Y %H:%M:%S"),
            parsed.strftime("%d.%m.%Y"),
        ]
        return list(dict.fromkeys(candidates))

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
            clean = value.strip().split(" ", 1)[0]
            return f"{clean} 00:00:00"

        status = command.ipu_status if command.ipu_status in {"Новый", "Годен", "Не Годен"} else "Годен"
        replacement = (
            "Да" if status == "Годен"
            else "Нет" if status == "Новый"
            else "Нет" if command.replacement_done
            else "Да"
        )
        last_check = "" if status == "Новый" else command.last_check
        next_check = "" if status == "Не Годен" else command.next_check
        attrs: dict[str, Any] = {
            self.fields["meter_address_id"]: application_attrs.get(self.fields["address_id"], ""),
            self.fields["meter_client_id"]: application_attrs.get(self.fields["client_id"], ""),
            self.fields["meter_application_id"]: str(application_id),
            self.fields["meter_device_kind"]: device_kind,
            self.fields["meter_type"]: command.meter_type,
            self.fields["meter_serial_number"]: command.serial_number,
            self.fields["meter_registry_number"]: command.registry_number,
            self.fields["meter_year"]: command.year,
            self.fields["meter_status"]: status,
        }
        if last_check.strip():
            last_value = crm_date(last_check)
            attrs[self.fields["meter_last_check"]] = last_value
            form_last_field = self.fields.get("meter_form_last_check")
            if form_last_field:
                attrs[form_last_field] = last_value
        if next_check.strip():
            next_value = crm_date(next_check)
            attrs[self.fields["meter_next_check"]] = next_value
            form_next_field = self.fields.get("meter_form_next_check")
            if form_next_field:
                attrs[form_next_field] = next_value
        replacement_field = self.fields.get("meter_replacement")
        if replacement_field:
            attrs[replacement_field] = replacement
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
            last_check="" if command.ipu_status == "Новый" else command.last_check,
            next_check="" if command.ipu_status == "Не Годен" else command.next_check,
            status=command.ipu_status,
            replacement=(
                "Да" if command.ipu_status == "Годен"
                else "Нет" if command.ipu_status == "Новый" or command.replacement_done
                else "Да"
            ),
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
                metadata = await self._request(
                    "GET", "table/351", params={"include": "fields"}
                )
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
                    replacement=str(
                        attrs.get(self.fields.get("meter_replacement", ""), "") or ""
                    ),
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

    async def rework_reasons(self) -> list[str]:
        try:
            metadata = await self._request(
                "GET", "table/130", params={"include": "fields"}
            )
        except ClientBaseError:
            return list(REWORK_REASON_FALLBACK)
        field_id = self.fields["rework_reason"]
        field_metadata = self._find_metadata_by_id(metadata, field_id)
        reasons = self._extract_choice_labels(field_metadata)
        return reasons or list(REWORK_REASON_FALLBACK)

    async def send_to_rework(
        self, application_id: int, reason: str, comment: str
    ) -> None:
        body = {
            "data": {
                "type": "data130",
                "id": str(application_id),
                "attributes": {
                    self.fields["rework_call_date"]: date.today().isoformat(),
                    self.fields["rework_reason"]: reason,
                    self.fields["metrolog_comments"]: comment,
                    self.fields["status"]: "На Доработку",
                },
            }
        }
        await self._request("PATCH", f"data130/{application_id}", json=body)

    @staticmethod
    def _find_metadata_by_id(value: Any, field_id: str) -> dict[str, Any]:
        numeric_id = field_id.removeprefix("f")
        if isinstance(value, dict):
            raw_id = str(value.get("id", ""))
            if raw_id in {field_id, numeric_id} or field_id in value:
                return value
            for nested in value.values():
                found = ClientBaseClient._find_metadata_by_id(nested, field_id)
                if found:
                    return found
        elif isinstance(value, list):
            for nested in value:
                found = ClientBaseClient._find_metadata_by_id(nested, field_id)
                if found:
                    return found
        return {}

    @staticmethod
    def _extract_choice_labels(field_metadata: dict[str, Any]) -> list[str]:
        container_names = {
            "choices", "enum", "items", "list", "options", "select", "values",
            "variants",
        }
        label_names = {"label", "name", "text", "title", "value"}
        labels: list[str] = []

        def add(raw: Any) -> None:
            value = str(raw or "").strip()
            if value and not re.fullmatch(r"f?\d+", value) and value not in labels:
                labels.append(value)

        def walk(value: Any, inside_choices: bool = False) -> None:
            if isinstance(value, list):
                for item in value:
                    if isinstance(item, dict):
                        label = next(
                            (
                                item[key]
                                for key in label_names
                                if key in item and isinstance(item[key], (str, int, float))
                            ),
                            None,
                        )
                        if inside_choices and label is not None:
                            add(label)
                        else:
                            walk(item, inside_choices)
                    elif inside_choices:
                        add(item)
            elif isinstance(value, dict):
                for key, nested in value.items():
                    walk(nested, inside_choices or key.casefold() in container_names)
            elif inside_choices and isinstance(value, str):
                parsed = None
                if value[:1] in "[{":
                    try:
                        parsed = json.loads(value)
                    except ValueError:
                        parsed = None
                if parsed is not None:
                    walk(parsed, True)
                else:
                    for item in re.split(r"[\r\n;]+", value):
                        add(item)

        walk(field_metadata)
        return labels

    async def _summary(self, item: dict) -> ApplicationSummary:
        attrs = item["attributes"]
        address, client = await asyncio.gather(
            self._related_value(
                "data470",
                attrs.get(self.fields["address_id"]),
                self.fields["address_value"],
            ),
            self._related_value(
                "data42",
                attrs.get(self.fields["client_id"]),
                self.fields["client_value"],
            ),
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
            phone_number_2=str(
                attrs.get(self.fields["phone_number_2"], "") or ""
            ),
            barrier=str(attrs.get(self.fields["barrier"], "") or ""),
            comments=str(attrs.get(self.fields["comments"], "") or ""),
        )

    async def _related_value(self, table: str, row_id: Any, field: str) -> str:
        if row_id in (None, ""):
            return ""
        cache_key = f"related:{table}:{row_id}:{field}"
        cached = self._cache_get(cache_key)
        if isinstance(cached, str):
            return cached
        payload = await self._request("GET", f"{table}/{row_id}")
        value = str(payload.get("data", {}).get("attributes", {}).get(field, "") or "")
        self._cache_set(cache_key, value, 21600)
        return value
