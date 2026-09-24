import asyncio

from app.client_base import APPLICATION_STATUSES, ClientBaseClient


def test_list_all_loads_every_page():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        calls = []

        async def fake_request(method, path, **kwargs):
            calls.append((method, path, kwargs["params"]))
            offset = kwargs["params"]["page[offset]"]
            if offset == 0:
                return {"data": [{"id": str(i)} for i in range(50)]}
            if offset == 50:
                return {"data": [{"id": "50"}]}
            raise AssertionError(f"unexpected offset: {offset}")

        client._request = fake_request
        rows = await client._list_all("data130", filter_expression="eq(status,0)")

        assert len(rows) == 51
        assert [call[2]["page[offset]"] for call in calls] == [0, 50]
        assert all(call[2]["page[limit]"] == 50 for call in calls)
        assert all(call[2]["filter"] == "eq(status,0)" for call in calls)

    asyncio.run(run_test())


def test_application_status_counts_keeps_defined_order_and_zeroes():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "application_metrolog_id": "f1740",
            "status": "f1680",
        }

        async def fake_employee_id(_crm_login):
            return 17

        async def fake_list_all(_path, **_kwargs):
            return [
                {"attributes": {"f1680": "Новая"}},
                {"attributes": {"f1680": "Новая"}},
                {"attributes": {"f1680": "Выполнено"}},
                {"attributes": {"f1680": "Неизвестный статус"}},
            ]

        client._employee_id = fake_employee_id
        client._list_all = fake_list_all
        result = await client.application_status_counts("demchenko")

        assert [item.status for item in result] == list(APPLICATION_STATUSES)
        assert [item.count for item in result] == [2, 1, 0]

    asyncio.run(run_test())


def test_water_meters_are_loaded_by_address():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "meter_address_id": "f11180",
            "meter_device_kind": "f10540",
            "meter_type": "f10580",
            "meter_modification": "f16061",
            "meter_accuracy_class": "f16071",
            "meter_serial_number": "f10550",
            "meter_registry_number": "f16051",
            "meter_year": "f16081",
            "meter_last_check": "f10560",
            "meter_next_check": "f10570",
            "meter_status": "f12740",
            "meter_reading": "f16131",
        }
        calls = []

        async def fake_request(method, path, **kwargs):
            calls.append((method, path, kwargs["params"]))
            return {
                "data": [
                    {
                        "id": "7",
                        "attributes": {
                            "f10540": "Счётчик воды",
                            "f10550": "SN-7",
                        },
                    }
                ]
            }

        client._request = fake_request
        result = await client._water_meters_by_address(42)

        assert len(result) == 1
        assert result[0].serial_number == "SN-7"
        assert calls[0][1] == "data610"
        assert "eq(f11180,42)" in calls[0][2]["filter"]

    asyncio.run(run_test())
