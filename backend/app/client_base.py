from datetime import date
from typing import Any

import httpx

from .config import Settings
from .models import (
    ApplicationDetails,
    ApplicationStatusCount,
    ApplicationSummary,
    CloseApplicationRequest,
    EmployeeProfile,
    WaterMeter,
)


APPLICATION_STATUSES = (
    "Новая",
    "Выполнено",
    "На Доработку",
)


class ClientBaseError(RuntimeError):
    pass


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
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.request(
                    method,
                    f"{self.base_url}/{path.lstrip('/')}",
                    headers=headers,
                    **kwargs,
                )
            response.raise_for_status()
            return response.json() if response.content else {}
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
        payload = await self._request(
            "GET",
            "data130",
            params={
                "filter": f"and({','.join(conditions)})",
                "page[limit]": 10,
                "page[offset]": 0,
            },
        )
        application_rows = payload.get("data", [])
        return [await self._summary(item) for item in application_rows]

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
        )

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
            phone_number=str(attrs.get(self.fields["phone_number"], "") or ""),
            phone_number_2=str(
                attrs.get(self.fields["phone_number_2"], "") or ""
            ),
            floor=str(attrs.get(self.fields["floor"], "") or ""),
            entrance=str(attrs.get(self.fields["entrance"], "") or ""),
            entrance_code=str(attrs.get(self.fields["entrance_code"], "") or ""),
            barrier=str(attrs.get(self.fields["barrier"], "") or ""),
            comments=str(attrs.get(self.fields["comments"], "") or ""),
            metrolog_comments=str(
                attrs.get(self.fields["metrolog_comments"], "") or ""
            ),
            water_meters=await self._water_meters_by_address(address_id),
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
            status=str(attrs.get(self.fields["status"], "")),
        )

    async def _related_value(self, table: str, row_id: Any, field: str) -> str:
        if row_id in (None, ""):
            return ""
        payload = await self._request("GET", f"{table}/{row_id}")
        return str(payload.get("data", {}).get("attributes", {}).get(field, "") or "")
