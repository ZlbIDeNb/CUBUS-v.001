from typing import Any

import httpx

from .config import Settings
from .models import ApplicationDetails, ApplicationSummary, CloseApplicationRequest


class ClientBaseError(RuntimeError):
    pass


class ClientBaseClient:
    """Client Base adapter. Field mapping is injected to avoid hard-coding PROD ids."""

    def __init__(self, settings: Settings, fields: dict[str, str]):
        self.base_url = settings.client_base_url.rstrip("/")
        self.timeout = settings.client_base_timeout_seconds
        self.headers = {"X-Auth-Token": settings.client_base_token.get_secret_value()}
        self.fields = fields

    async def _request(self, method: str, path: str, **kwargs: Any) -> dict:
        headers = dict(self.headers)
        if method in {"PATCH", "POST"}:
            headers["Content-Type"] = "application/vnd.api+json"
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

    async def list_applications(self, crm_login: str) -> list[ApplicationSummary]:
        # Endpoint mapping follows the legacy bot: employee=data46, application=data130.
        employee_field = self.fields["employee_login"]
        employee_id_field = self.fields["employee_id"]
        condition = f"and(eq(status,0),eq({employee_field},'{crm_login}'))"
        employee = await self._request("GET", f"data46?filter={condition}")
        rows = employee.get("data", [])
        if not rows:
            return []
        employee_id = rows[0]["attributes"][employee_id_field]
        metrolog_field = self.fields["application_metrolog_id"]
        applications = await self._request(
            "GET", f"data130?filter=and(eq(status,0),eq({metrolog_field},{employee_id}))"
        )
        return [await self._summary(item) for item in applications.get("data", [])]

    async def get_application(self, application_id: int) -> ApplicationDetails:
        payload = await self._request("GET", f"data130/{application_id}")
        item = payload["data"]
        attrs = item["attributes"]
        summary = await self._summary(item)
        return ApplicationDetails(
            **summary.model_dump(),
            phone_number=str(attrs.get(self.fields["phone_number"], "") or ""),
            floor=str(attrs.get(self.fields["floor"], "") or ""),
            entrance=str(attrs.get(self.fields["entrance"], "") or ""),
            entrance_code=str(attrs.get(self.fields["entrance_code"], "") or ""),
            barrier=str(attrs.get(self.fields["barrier"], "") or ""),
            comments=str(attrs.get(self.fields["comments"], "") or ""),
        )

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
