import asyncio
import base64
from datetime import date

import httpx

from app.client_base import APPLICATION_STATUSES, ClientBaseClient, ClientBaseError
from app.models import AddMeterRequest, AddNomenclatureRequest


def test_rework_reasons_are_read_from_field_metadata():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {"rework_reason": "f13630"}

        async def fake_request(method, path, **kwargs):
            assert method == "GET"
            assert path == "table/130"
            assert kwargs["params"] == {"include": "fields"}
            return {
                "data": {"attributes": {"fields": [
                    {"id": "13630", "name": "Причина", "options": [
                        {"value": "Клиент не отвечает"},
                        {"value": "Перенос даты"},
                    ]}
                ]}}
            }

        client._request = fake_request
        assert await client.rework_reasons() == ["Клиент не отвечает", "Перенос даты"]

    asyncio.run(run_test())


def test_send_to_rework_writes_date_reason_comment_and_status():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "rework_call_date": "f13620",
            "rework_reason": "f13630",
            "metrolog_comments": "f17940",
            "status": "f1680",
        }
        captured = {}

        async def fake_request(method, path, **kwargs):
            captured.update(method=method, path=path, body=kwargs["json"])
            return {}

        client._request = fake_request
        await client.send_to_rework(40005, "Перенос даты", "Позвонить завтра")
        attrs = captured["body"]["data"]["attributes"]
        assert captured["method"] == "PATCH"
        assert captured["path"] == "data130/40005"
        assert attrs == {
            "f13620": date.today().isoformat(),
            "f13630": "Перенос даты",
            "f17940": "Позвонить завтра",
            "f1680": "На Доработку",
        }

    asyncio.run(run_test())


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
            "meter_device_photo": "f17860",
            "meter_passport_photo": "f18021",
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


def test_nomenclature_field_is_discovered_from_table_metadata():
    metadata = {
        "data": {
            "attributes": {
                "fields": [
                    {"id": "f9001", "name": "Связь с заявкой"},
                    {"id": "f9002", "name": "Наименование из прайс-листа"},
                ]
            }
        }
    }

    assert ClientBaseClient._find_field_id(metadata, ("заяв",)) == "f9001"
    assert ClientBaseClient._find_field_id(metadata, ("наименование", "прайс")) == "f9002"


def test_table_220_overrides_generic_employee_schedule():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "employee_id": "f1400",
            "employee_work_days": "f4901",
            "employee_work_schedule": "f15711",
            "employee_schedule_control": "f2980",
            "schedule_employee": "f3110",
            "schedule_date": "f3120",
            "schedule_status": "f13391",
        }

        async def fake_employee_row(_login):
            return {"id": "501", "attributes": {"f1400": "77", "f15711": "Пн-Пт"}}

        async def fake_list_all(path, **kwargs):
            assert path == "data220"
            assert "eq(f3110,501)" in kwargs["filter_expression"]
            assert "gte(f3120,'2026-09-01 00:00:00')" in kwargs["filter_expression"]
            return [
                {"attributes": {"f3110": "501", "f3120": "2026-09-05 00:00:00", "f13391": "Работает"}},
                {"attributes": {"f3110": "501", "f3120": "2026-09-07 00:00:00", "f13391": "Выходной"}},
            ]

        client._employee_row = fake_employee_row
        client._list_all = fake_list_all
        result = await client.employee_schedule("employee", 2026, 9)

        assert result[4].is_working is True
        assert result[6].is_working is False

    asyncio.run(run_test())


def test_table_220_supports_vacation_status():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "employee_id": "f1400",
            "employee_work_days": "f4901", "employee_work_schedule": "f15711",
            "employee_schedule_control": "f2980",
            "schedule_employee": "f3110", "schedule_date": "f3120",
            "schedule_status": "f13391",
        }

        async def fake_employee_row(_login):
            return {"id": "501", "attributes": {"f1400": "77"}}

        async def fake_list_all(_path, **_kwargs):
            return [
                {"attributes": {"f3110": "501", "f3120": "05.09.2026", "f13391": "Отпуск"}},
            ]

        client._employee_row = fake_employee_row
        client._list_all = fake_list_all
        result = await client.employee_schedule("employee", 2026, 9)

        assert result[4].has_record is True
        assert result[4].is_working is False
        assert result[4].work_status == "Отпуск"

    asyncio.run(run_test())


def test_numeric_field_id_and_nested_file_content_are_normalized():
    metadata = {"data": {"attributes": {"fields": [{"id": 9200, "name": "Цена"}]}}}
    payload = {"data": {"attributes": {"name": "pic.png", "content": "YWJj"}}}

    assert ClientBaseClient._find_field_id(metadata, ("цена",)) == "f9200"
    assert ClientBaseClient._extract_file_data(payload)["content"] == "YWJj"


def test_nomenclature_probes_subtable_relation_field():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "nomenclature_application_id": "",
            "nomenclature_name": "f8080",
            "nomenclature_price": "f8090",
            "nomenclature_quantity": "f8100",
            "nomenclature_total": "f8110",
        }

        async def fake_request(method, path, **kwargs):
            if path == "table/351":
                return {"data": {"attributes": {}}}
            params = kwargs["params"]
            if params.get("page[limit]") == 1:
                return {
                    "data": [{"id": "1", "attributes": {
                        "f8080": "Работа", "f8090": "100", "f8100": "2",
                        "f8110": "200", "f9990": "39801",
                    }}]
                }
            if "f9990" in params["filter"]:
                return {"data": [{"id": "1", "attributes": {
                    "f8080": "Работа", "f8090": "100", "f8100": "2",
                    "f8110": "200", "f9990": "39801",
                }}]}
            return {"data": []}

        client._request = fake_request
        result = await client.nomenclature(39801)

        assert len(result) == 1
        assert result[0].name == "Работа"
        assert result[0].total == "200"

    asyncio.run(run_test())


def test_employee_equipment_is_built_from_employee_card():
    client = object.__new__(ClientBaseClient)
    field_names = (
        "equipment_installation_name", "equipment_installation_serial",
        "equipment_installation_registry", "equipment_installation_certificate",
        "equipment_installation_date", "equipment_installation_arshin",
        "equipment_stopwatch_name", "equipment_stopwatch_serial",
        "equipment_stopwatch_registry", "equipment_stopwatch_date",
        "equipment_stopwatch_arshin", "equipment_hygrometer_name",
        "equipment_hygrometer_serial", "equipment_hygrometer_registry",
        "equipment_hygrometer_date", "equipment_hygrometer_arshin",
        "equipment_thermometer_name", "equipment_thermometer_serial",
        "equipment_thermometer_registry", "equipment_thermometer_date",
        "equipment_thermometer_arshin", "equipment_acquiring_name",
        "equipment_acquiring_serial",
    )
    client.fields = {name: f"f{index}" for index, name in enumerate(field_names)}
    attrs = {
        client.fields["equipment_installation_name"]: "Стандарт-ВМ",
        client.fields["equipment_acquiring_name"]: "POS Terminal",
    }

    equipment = client._employee_equipment(attrs)

    assert [item.category for item in equipment] == [
        "Поверочная установка", "Секундомер", "Термогигрометр",
        "Термометр", "Эквайринг",
    ]
    assert equipment[0].name == "Стандарт-ВМ"
    assert equipment[-1].name == "POS Terminal"


def test_file_list_fallback_selects_requested_filename():
    payload = {
        "data": [
            {"attributes": {"name": "first.png", "content": "MQ=="}},
            {"attributes": {"name": "pic1.png", "content": "Mg=="}},
        ]
    }

    result = ClientBaseClient._extract_file_data(payload, "pic1.png")

    assert result["content"] == "Mg=="


def test_price_list_relation_is_resolved_to_name():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {"price_list_name": "f1158"}

        async def fake_request(method, path, **kwargs):
            assert method == "GET"
            assert path == "data91/24"
            return {"data": {"attributes": {"f1158": "Удлинитель 1/2"}}}

        client._request = fake_request

        assert await client._price_list_name("24") == "Удлинитель 1/2"

    asyncio.run(run_test())


def test_binary_file_response_is_converted_to_base64():
    response = httpx.Response(
        200,
        headers={"content-type": "image/png"},
        content=b"\x89PNG\r\n",
    )

    encoded = ClientBaseClient._decode_file_response(response, "pic.png")

    assert base64.b64decode(encoded) == b"\x89PNG\r\n"


def test_json_file_response_keeps_clientbase_base64():
    response = httpx.Response(
        200,
        headers={"content-type": "application/vnd.api+json"},
        json={"data": {"name": "pic.png", "content": "YWJj"}},
    )

    assert ClientBaseClient._decode_file_response(response, "pic.png") == "YWJj"


def test_deleting_only_file_does_not_download_broken_file():
    async def run_test():
        client = object.__new__(ClientBaseClient)

        async def fake_request(method, path, **kwargs):
            assert path == "data130/39801"
            return {"data": {"attributes": {"f12770": "pic1.png"}}}

        async def unexpected_photo_content(*_args, **_kwargs):
            raise AssertionError("deleted file must not be downloaded")

        client._request = fake_request
        client.photo_content = unexpected_photo_content

        files = await client._file_payloads(
            39801, "f12770", exclude_filename="pic1.png"
        )

        assert files == []

    asyncio.run(run_test())


def test_photo_is_searched_in_other_photo_fields_after_404():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "photo_act": "f1730",
            "photo_replacement_act": "f12770",
        }
        calls = []

        async def fake_download(application_id, field, filename):
            calls.append((application_id, field, filename))
            if field == "f12770":
                raise ClientBaseError("not found", status_code=404)
            return "YWJj"

        client._download_photo = fake_download

        content = await client.photo_content(39801, "f12770", "pic1.png")

        assert content == "YWJj"
        assert [call[1] for call in calls] == ["f12770", "f1730"]

    asyncio.run(run_test())


def test_add_nomenclature_uses_application_and_price_list_relations():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "price_list_name": "f1158", "price_list_price": "f1169",
            "nomenclature_application_id": "f5651", "nomenclature_name": "f5671",
            "nomenclature_name_fallback": "f5681", "nomenclature_price": "f5701",
            "nomenclature_quantity": "f5711", "nomenclature_total": "f5721",
        }
        created = {}

        async def fake_request(method, path, **kwargs):
            if path == "data91/24":
                return {"data": {"attributes": {"f1158": "Кран", "f1169": "250"}}}
            created.update(kwargs["json"]["data"]["attributes"])
            return {"data": {}}

        client._request = fake_request
        await client.add_nomenclature(39801, AddNomenclatureRequest(price_list_id=24, quantity=2))

        assert created["f5651"] == "39801"
        assert created["f5671"] == "24"
        assert created["f5681"] == "Кран"
        assert created["f5721"] == "500.0"

    asyncio.run(run_test())


def test_add_meter_links_address_client_and_application():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "address_id": "f11180", "client_id": "f11360",
            "meter_address_id": "f11180", "meter_client_id": "f11360",
            "meter_application_id": "f11440", "meter_device_kind": "f10540",
            "meter_type": "f10580", "meter_serial_number": "f10550",
            "meter_registry_number": "f16051", "meter_year": "f16081",
            "meter_last_check": "f10560", "meter_next_check": "f10570",
            "meter_status": "f12740",
            "meter_replacement": "f17640",
            "meter_device_photo": "f17860", "meter_passport_photo": "f18021",
        }
        created = {}

        async def fake_request(method, path, **kwargs):
            if path == "data130/39801":
                return {"data": {"attributes": {"f11180": "55", "f11360": "66"}}}
            created.update(kwargs["json"]["data"]["attributes"])
            return {"data": {"id": "900"}}

        client._request = fake_request
        await client.add_meter(
            39801,
            AddMeterRequest(
                device_kind="ИПУ ХВС",
                meter_type="СВК",
                serial_number="123",
                device_photo_filename="meter.jpg",
                device_photo_base64="YWJj",
            ),
        )

        assert created["f11180"] == "55"
        assert created["f11360"] == "66"
        assert created["f11440"] == "39801"
        assert created["f10550"] == "123"
        assert created["f10540"] == "ИПУ ХВС"
        assert created["f12740"] == "Годен"
        assert created["f17640"] == "Да"
        assert created["f17860"][0]["file_name"] == "meter.jpg"

    asyncio.run(run_test())


def test_unfit_meter_with_replacement_sets_required_clientbase_values():
    client = object.__new__(ClientBaseClient)
    client.fields = {
        "address_id": "address", "client_id": "client",
        "meter_address_id": "meter_address", "meter_client_id": "meter_client",
        "meter_application_id": "application", "meter_device_kind": "kind",
        "meter_type": "type", "meter_serial_number": "serial",
        "meter_registry_number": "registry", "meter_year": "year",
        "meter_last_check": "last", "meter_next_check": "next",
        "meter_status": "status", "meter_replacement": "replacement",
        "meter_device_photo": "photo", "meter_passport_photo": "passport",
    }
    command = AddMeterRequest(
        device_kind="ИПУ ГВС",
        ipu_status="Не Годен",
        replacement_done=True,
        last_check="2026-09-24",
        next_check="2030-09-23",
    )

    attrs = client._meter_attributes({"address": "1", "client": "2"}, 3, command)

    assert attrs["status"] == "Не Годен"
    assert attrs["replacement"] == "Нет"
    assert attrs["last"] == "2026-09-24 00:00:00"
    assert attrs["next"] == ""


def test_meter_catalog_loads_sorted_dictionary_and_searches_both_values():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {
            "meter_catalog_registry_number": "f17120",
            "meter_catalog_designation": "f17130",
        }

        async def fake_list_all(path, filter_expression):
            assert path == "data940"
            return [
                {"id": "1", "attributes": {"f17120": "123-45", "f17130": "СВК-15"}},
                {"id": "2", "attributes": {"f17120": "987-65", "f17130": "Тепловодомер"}},
            ]

        client._list_all = fake_list_all

        by_registry = await client.meter_catalog("123")
        by_designation = await client.meter_catalog("тепло")
        all_items = await client.meter_catalog()

        assert by_registry[0].designation == "СВК-15"
        assert by_designation[0].registry_number == "987-65"
        assert [item.designation for item in all_items] == ["СВК-15", "Тепловодомер"]

    asyncio.run(run_test())


def test_delete_nomenclature_checks_application_relation():
    async def run_test():
        client = object.__new__(ClientBaseClient)
        client.fields = {"nomenclature_application_id": "f5651"}
        calls = []

        async def fake_request(method, path, **kwargs):
            calls.append((method, path))
            return {"data": {"attributes": {"f5651": "39801"}}}

        client._request = fake_request
        await client.delete_nomenclature(39801, 77)

        assert calls == [("GET", "data351/77"), ("DELETE", "data351/77")]

    asyncio.run(run_test())
