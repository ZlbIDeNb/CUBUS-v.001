from datetime import date

from app.models import (
    ApplicationDetails,
    ApplicationStatusCount,
    ApplicationSummary,
    TodayStatistics,
    WaterMeter,
)
from app.operations_store import OperationsStore


def application(application_id: int, phone: str = "+7 999 111-22-33") -> ApplicationSummary:
    return ApplicationSummary(
        id=application_id,
        number=str(application_id),
        work_date=date(2026, 9, 25),
        address="г. Москва, ул. Тверская, д. 1",
        client="Иванов Иван",
        status="Выполнено",
        phone_number=phone,
    )


def test_registry_deduplicates_address_client_and_phone(tmp_path):
    store = OperationsStore(tmp_path)
    store.sync_application(application(1), "metrolog-1")
    store.sync_application(application(2, "8 (999) 111-22-33"), "metrolog-2")

    stats = store.stats()
    addresses = store.addresses()

    assert stats.addresses == 1
    assert stats.clients == 1
    assert stats.phones == 1
    assert stats.applications == 2
    assert addresses[0].clients[0].phones[0].phone == "8 (999) 111-22-33"


def test_registry_links_meters_to_central_address(tmp_path):
    store = OperationsStore(tmp_path)
    summary = application(1)
    details = ApplicationDetails(
        **summary.model_dump(),
        water_meters=[WaterMeter(id=77, device_kind="ХВС", serial_number="ABC-1")],
    )

    store.sync_application(details, "metrolog-1", details)
    store.sync_application(details, "metrolog-1", details)

    assert store.stats().meters == 1
    assert store.addresses()[0].meters[0].serial_number == "ABC-1"


def test_activity_history_keeps_real_actor_and_target(tmp_path):
    store = OperationsStore(tmp_path)
    store.log(
        {"email": "admin@example.ru", "sub": "admin"},
        "Заменён документ администратором",
        target_login="metrolog-1",
        entity_type="document",
        entity_id=12,
    )

    event = store.history("metrolog-1")[0]

    assert event.actor_email == "admin@example.ru"
    assert event.target_login == "metrolog-1"
    assert event.entity_id == "12"


def test_daily_statistics_are_updated_per_day_and_isolated_by_employee(tmp_path):
    store = OperationsStore(tmp_path)
    work_date = date.today()
    first = [
        ApplicationStatusCount(status="Новая", count=76),
        ApplicationStatusCount(status="Выполнено", count=12),
        ApplicationStatusCount(status="На Доработку", count=3),
        ApplicationStatusCount(status="Отложено", count=4),
        ApplicationStatusCount(status="Отказ", count=2),
    ]
    updated = [*first[:1], ApplicationStatusCount(status="Выполнено", count=15), *first[2:]]

    store.save_daily_statistics("metrolog-1", work_date, first)
    store.save_daily_statistics("metrolog-1", work_date, updated)
    store.save_daily_statistics("metrolog-2", work_date, [
        ApplicationStatusCount(status="Новая", count=8),
    ])

    first_history = store.daily_statistics("metrolog-1")
    second_history = store.daily_statistics("metrolog-2")

    assert len(first_history) == 1
    assert first_history[0].new_count == 76
    assert first_history[0].completed == 15
    assert second_history[0].new_count == 8


def test_tracking_baseline_and_snapshots_are_isolated_by_employee(tmp_path):
    store = OperationsStore(tmp_path)
    work_date = date.today()
    baseline = store.create_tracking_baseline(
        "metrolog-1", work_date, [application(101), application(102)]
    )
    store.create_tracking_baseline("metrolog-2", work_date, [application(201)])
    snapshot = TodayStatistics(
        work_date=work_date,
        initial_new=2,
        completed=1,
        rework=0,
        postponed=0,
        refusals=0,
        remaining_new=1,
        moved_to_other_date=0,
        transferred_to_other_employee=0,
        added_later=0,
        snapshot_at="2026-09-25T12:00:00+00:00",
    )
    store.save_tracking_snapshot("metrolog-1", snapshot)

    assert set(baseline) == {101, 102}
    assert set(baseline.values()) == {"Выполнено"}
    assert set(store.tracking_baseline("metrolog-2", work_date)) == {201}
    assert store.tracking_snapshots("metrolog-1", work_date) == [snapshot]
    assert store.tracking_snapshots("metrolog-2", work_date) == []


def test_scheduled_capture_slot_is_marked_once(tmp_path):
    store = OperationsStore(tmp_path)
    work_date = date.today()

    assert not store.scheduled_capture_done("__company__", work_date, 9)
    store.mark_scheduled_capture_done("__company__", work_date, 9)
    store.mark_scheduled_capture_done("__company__", work_date, 9)

    assert store.scheduled_capture_done("__company__", work_date, 9)
