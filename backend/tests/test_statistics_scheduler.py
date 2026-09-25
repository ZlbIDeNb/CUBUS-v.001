import asyncio
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from app import main as main_module
from app.main import capture_today_statistics, latest_statistics_slot
from app.models import ApplicationSummary
from app.operations_store import OperationsStore


MOSCOW = ZoneInfo("Europe/Moscow")


def test_latest_statistics_slot_during_workday():
    work_date, slot = latest_statistics_slot(
        datetime(2026, 9, 25, 17, 40, tzinfo=MOSCOW)
    )

    assert work_date.isoformat() == "2026-09-25"
    assert slot == 17 * 60 + 30


def test_midnight_slot_finalizes_previous_day():
    work_date, slot = latest_statistics_slot(
        datetime(2026, 9, 26, 0, 10, tzinfo=MOSCOW)
    )

    assert work_date.isoformat() == "2026-09-25"
    assert slot == 24 * 60


def test_admin_initial_snapshot_uses_company_date_and_new_status(tmp_path, monkeypatch):
    work_date = date(2026, 9, 25)
    rows = [
        ApplicationSummary(
            id=index,
            number=str(40_000 + index),
            work_date=work_date,
            status="Новая",
        )
        for index in range(1, 77)
    ]

    class FakeCrm:
        async def list_company_tracking_applications(self, requested_date):
            assert requested_date == work_date
            return rows

    monkeypatch.setattr(main_module, "operations_store", OperationsStore(tmp_path))
    result = asyncio.run(capture_today_statistics(
        work_date,
        {"sub": "admin", "role": "Администратор"},
        FakeCrm(),
    ))

    assert result.initial_new == 76
    assert result.remaining_new == 76
    assert result.completed == 0


def test_admin_snapshot_tracks_status_and_date_changes(tmp_path, monkeypatch):
    work_date = date(2026, 9, 25)
    initial = [
        ApplicationSummary(id=index, number=str(index), work_date=work_date, status="Новая")
        for index in range(1, 5)
    ]

    class FakeCrm:
        calls = 0

        async def list_company_tracking_applications(self, requested_date):
            self.calls += 1
            if self.calls == 1:
                return initial
            return [initial[0]]

        async def get_application_tracking_state(self, application_id):
            states = {
                2: ApplicationSummary(id=2, number="2", work_date=work_date, status="Выполнено"),
                3: ApplicationSummary(id=3, number="3", work_date=work_date, status="Отложено"),
                4: ApplicationSummary(
                    id=4, number="4", work_date=work_date + timedelta(days=1), status="Новая"
                ),
            }
            return states[application_id]

    crm = FakeCrm()
    monkeypatch.setattr(main_module, "operations_store", OperationsStore(tmp_path))
    admin = {"sub": "admin", "role": "Администратор"}
    asyncio.run(capture_today_statistics(work_date, admin, crm))
    result = asyncio.run(capture_today_statistics(work_date, admin, crm))

    assert result.initial_new == 4
    assert result.remaining_new == 1
    assert result.completed == 1
    assert result.postponed == 1
    assert result.moved_to_other_date == 1
