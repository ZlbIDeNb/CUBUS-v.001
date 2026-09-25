from __future__ import annotations

import re
import sqlite3
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

from .models import (
    ActivityLogItem,
    ApplicationDetails,
    ApplicationSummary,
    ApplicationStatusCount,
    DailyStatistics,
    TodayStatistics,
    RegistryAddress,
    RegistryClient,
    RegistryMeter,
    RegistryPhone,
    RegistryStats,
)


def _text_key(value: str) -> str:
    return " ".join(value.casefold().replace("ё", "е").split())


def _phone_key(value: str) -> str:
    digits = re.sub(r"\D", "", value)
    if len(digits) == 11 and digits.startswith("8"):
        digits = "7" + digits[1:]
    return digits


class OperationsStore:
    def __init__(self, root: Path | None = None) -> None:
        self.root = root or Path(__file__).resolve().parent.parent / "server_registry"
        self.root.mkdir(parents=True, exist_ok=True)
        self.database = self.root / "registry.sqlite3"
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.database)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        return connection

    def _initialize(self) -> None:
        with self._connect() as db:
            db.executescript(
                """
                CREATE TABLE IF NOT EXISTS addresses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    normalized_address TEXT NOT NULL UNIQUE,
                    address TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS clients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    address_id INTEGER NOT NULL REFERENCES addresses(id) ON DELETE CASCADE,
                    normalized_name TEXT NOT NULL,
                    name TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    UNIQUE(address_id, normalized_name)
                );
                CREATE TABLE IF NOT EXISTS phones (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    client_id INTEGER NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
                    normalized_phone TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    UNIQUE(client_id, normalized_phone)
                );
                CREATE TABLE IF NOT EXISTS meters (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    address_id INTEGER NOT NULL REFERENCES addresses(id) ON DELETE CASCADE,
                    source_meter_id INTEGER,
                    identity_key TEXT NOT NULL,
                    device_kind TEXT NOT NULL DEFAULT '',
                    meter_type TEXT NOT NULL DEFAULT '',
                    serial_number TEXT NOT NULL DEFAULT '',
                    registry_number TEXT NOT NULL DEFAULT '',
                    year TEXT NOT NULL DEFAULT '',
                    last_check TEXT NOT NULL DEFAULT '',
                    next_check TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT '',
                    updated_at TEXT NOT NULL,
                    UNIQUE(address_id, identity_key)
                );
                CREATE TABLE IF NOT EXISTS source_applications (
                    application_id INTEGER PRIMARY KEY,
                    address_id INTEGER NOT NULL REFERENCES addresses(id) ON DELETE CASCADE,
                    client_id INTEGER REFERENCES clients(id) ON DELETE SET NULL,
                    metrolog_login TEXT NOT NULL,
                    number TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT '',
                    work_date TEXT,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS activity_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    actor_email TEXT NOT NULL DEFAULT '',
                    actor_login TEXT NOT NULL,
                    target_login TEXT NOT NULL,
                    action TEXT NOT NULL,
                    entity_type TEXT NOT NULL DEFAULT '',
                    entity_id TEXT NOT NULL DEFAULT '',
                    details TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS activity_target_idx
                    ON activity_log(target_login, id DESC);
                CREATE INDEX IF NOT EXISTS address_search_idx ON addresses(address);
                CREATE TABLE IF NOT EXISTS daily_statistics (
                    metrolog_login TEXT NOT NULL,
                    work_date TEXT NOT NULL,
                    total INTEGER NOT NULL DEFAULT 0,
                    new_count INTEGER NOT NULL DEFAULT 0,
                    completed INTEGER NOT NULL DEFAULT 0,
                    rework INTEGER NOT NULL DEFAULT 0,
                    postponed INTEGER NOT NULL DEFAULT 0,
                    refusals INTEGER NOT NULL DEFAULT 0,
                    recorded_at TEXT NOT NULL,
                    PRIMARY KEY(metrolog_login, work_date)
                );
                CREATE INDEX IF NOT EXISTS daily_statistics_login_date_idx
                    ON daily_statistics(metrolog_login, work_date DESC);
                CREATE TABLE IF NOT EXISTS daily_tracking_baseline (
                    metrolog_login TEXT NOT NULL,
                    work_date TEXT NOT NULL,
                    application_id INTEGER NOT NULL,
                    number TEXT NOT NULL DEFAULT '',
                    initial_status TEXT NOT NULL DEFAULT '',
                    initial_work_date TEXT,
                    is_initial INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY(metrolog_login, work_date, application_id)
                );
                CREATE TABLE IF NOT EXISTS daily_tracking_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    metrolog_login TEXT NOT NULL,
                    work_date TEXT NOT NULL,
                    initial_new INTEGER NOT NULL DEFAULT 0,
                    completed INTEGER NOT NULL DEFAULT 0,
                    rework INTEGER NOT NULL DEFAULT 0,
                    postponed INTEGER NOT NULL DEFAULT 0,
                    refusals INTEGER NOT NULL DEFAULT 0,
                    remaining_new INTEGER NOT NULL DEFAULT 0,
                    moved_to_other_date INTEGER NOT NULL DEFAULT 0,
                    transferred_to_other_employee INTEGER NOT NULL DEFAULT 0,
                    added_later INTEGER NOT NULL DEFAULT 0,
                    snapshot_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS daily_tracking_snapshot_idx
                    ON daily_tracking_snapshots(metrolog_login, work_date, id DESC);
                CREATE TABLE IF NOT EXISTS statistics_schedule_runs (
                    scope TEXT NOT NULL,
                    work_date TEXT NOT NULL,
                    scheduled_hour INTEGER NOT NULL,
                    completed_at TEXT NOT NULL,
                    PRIMARY KEY(scope, work_date, scheduled_hour)
                );
                """
            )
            baseline_columns = {
                row["name"]
                for row in db.execute("PRAGMA table_info(daily_tracking_baseline)")
            }
            if "initial_status" not in baseline_columns:
                db.execute(
                    """ALTER TABLE daily_tracking_baseline
                    ADD COLUMN initial_status TEXT NOT NULL DEFAULT ''"""
                )
            if "initial_work_date" not in baseline_columns:
                db.execute(
                    """ALTER TABLE daily_tracking_baseline
                    ADD COLUMN initial_work_date TEXT"""
                )
            if "is_initial" not in baseline_columns:
                db.execute(
                    """ALTER TABLE daily_tracking_baseline
                    ADD COLUMN is_initial INTEGER NOT NULL DEFAULT 1"""
                )

    def tracking_baseline(
        self, metrolog_login: str, work_date: date
    ) -> dict[int, str]:
        with self._connect() as db:
            rows = db.execute(
                """SELECT application_id,initial_status FROM daily_tracking_baseline
                    WHERE metrolog_login=? AND work_date=?""",
                (metrolog_login, work_date.isoformat()),
            ).fetchall()
        return {
            int(row["application_id"]): str(row["initial_status"]) for row in rows
        }

    def create_tracking_baseline(
        self,
        metrolog_login: str,
        work_date: date,
        applications: list[ApplicationSummary],
        is_initial: bool = True,
    ) -> dict[int, str]:
        now = datetime.now(UTC).isoformat()
        with self._connect() as db:
            db.executemany(
                """INSERT OR IGNORE INTO daily_tracking_baseline(
                    metrolog_login,work_date,application_id,number,initial_status,
                    initial_work_date,is_initial,created_at
                ) VALUES(?,?,?,?,?,?,?,?)""",
                [
                    (
                        metrolog_login, work_date.isoformat(), item.id, item.number,
                        item.status,
                        item.work_date.isoformat() if item.work_date else None,
                        1 if is_initial else 0,
                        now,
                    )
                    for item in applications
                ],
            )
        return self.tracking_baseline(metrolog_login, work_date)

    def tracking_initial_ids(self, metrolog_login: str, work_date: date) -> set[int]:
        with self._connect() as db:
            rows = db.execute(
                """SELECT application_id FROM daily_tracking_baseline
                    WHERE metrolog_login=? AND work_date=? AND is_initial=1""",
                (metrolog_login, work_date.isoformat()),
            ).fetchall()
        return {int(row["application_id"]) for row in rows}

    def tracking_initial_dates(
        self, metrolog_login: str, work_date: date
    ) -> dict[int, date | None]:
        with self._connect() as db:
            rows = db.execute(
                """SELECT application_id,initial_work_date
                    FROM daily_tracking_baseline
                    WHERE metrolog_login=? AND work_date=?""",
                (metrolog_login, work_date.isoformat()),
            ).fetchall()
        return {
            int(row["application_id"]): (
                date.fromisoformat(row["initial_work_date"])
                if row["initial_work_date"] else None
            )
            for row in rows
        }

    def save_tracking_snapshot(
        self, metrolog_login: str, statistics: TodayStatistics
    ) -> TodayStatistics:
        with self._connect() as db:
            db.execute(
                """INSERT INTO daily_tracking_snapshots(
                    metrolog_login,work_date,initial_new,completed,rework,postponed,
                    refusals,remaining_new,moved_to_other_date,
                    transferred_to_other_employee,added_later,snapshot_at
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
                (
                    metrolog_login, statistics.work_date.isoformat(),
                    statistics.initial_new, statistics.completed, statistics.rework,
                    statistics.postponed, statistics.refusals, statistics.remaining_new,
                    statistics.moved_to_other_date,
                    statistics.transferred_to_other_employee,
                    statistics.added_later, statistics.snapshot_at,
                ),
            )
        return statistics

    def tracking_snapshots(
        self, metrolog_login: str, work_date: date
    ) -> list[TodayStatistics]:
        with self._connect() as db:
            rows = db.execute(
                """SELECT work_date,initial_new,completed,rework,postponed,
                    refusals,remaining_new,moved_to_other_date,
                    transferred_to_other_employee,added_later,snapshot_at
                    FROM daily_tracking_snapshots
                    WHERE metrolog_login=? AND work_date=? ORDER BY id DESC""",
                (metrolog_login, work_date.isoformat()),
            ).fetchall()
        return [TodayStatistics(**dict(row)) for row in rows]

    def latest_tracking_snapshot(
        self, metrolog_login: str, work_date: date
    ) -> TodayStatistics | None:
        snapshots = self.tracking_snapshots(metrolog_login, work_date)
        return snapshots[0] if snapshots else None

    def scheduled_capture_done(
        self, scope: str, work_date: date, scheduled_hour: int
    ) -> bool:
        with self._connect() as db:
            row = db.execute(
                """SELECT 1 FROM statistics_schedule_runs
                    WHERE scope=? AND work_date=? AND scheduled_hour=?""",
                (scope, work_date.isoformat(), scheduled_hour),
            ).fetchone()
        return row is not None

    def mark_scheduled_capture_done(
        self, scope: str, work_date: date, scheduled_hour: int
    ) -> None:
        with self._connect() as db:
            db.execute(
                """INSERT OR IGNORE INTO statistics_schedule_runs(
                    scope,work_date,scheduled_hour,completed_at
                ) VALUES(?,?,?,?)""",
                (
                    scope, work_date.isoformat(), scheduled_hour,
                    datetime.now(UTC).isoformat(),
                ),
            )

    def save_daily_statistics(
        self,
        metrolog_login: str,
        work_date: date,
        counts: list[ApplicationStatusCount],
    ) -> DailyStatistics:
        values = {item.status: item.count for item in counts}
        recorded_at = datetime.now(UTC).isoformat()
        payload = DailyStatistics(
            work_date=work_date,
            total=values.get("Всего", sum(values.values())),
            new_count=values.get("Новая", 0),
            completed=values.get("Выполнено", 0),
            rework=values.get("На Доработку", 0),
            postponed=values.get("Отложено", 0),
            refusals=values.get("Отказ", 0),
            recorded_at=recorded_at,
        )
        with self._connect() as db:
            db.execute(
                """INSERT INTO daily_statistics(
                    metrolog_login,work_date,total,new_count,completed,rework,
                    postponed,refusals,recorded_at
                ) VALUES(?,?,?,?,?,?,?,?,?)
                ON CONFLICT(metrolog_login,work_date) DO UPDATE SET
                    total=excluded.total,new_count=excluded.new_count,
                    completed=excluded.completed,rework=excluded.rework,
                    postponed=excluded.postponed,refusals=excluded.refusals,
                    recorded_at=excluded.recorded_at""",
                (
                    metrolog_login, work_date.isoformat(), payload.total,
                    payload.new_count, payload.completed, payload.rework,
                    payload.postponed, payload.refusals, recorded_at,
                ),
            )
        return payload

    def daily_statistics(self, metrolog_login: str, days: int = 31) -> list[DailyStatistics]:
        safe_days = max(1, min(days, 366))
        first_day = (date.today() - timedelta(days=safe_days - 1)).isoformat()
        with self._connect() as db:
            rows = db.execute(
                """SELECT work_date,total,new_count,completed,rework,postponed,
                    refusals,recorded_at FROM daily_statistics
                    WHERE metrolog_login=? AND work_date>=?
                    ORDER BY work_date DESC""",
                (metrolog_login, first_day),
            ).fetchall()
        return [DailyStatistics(**dict(row)) for row in rows]

    def log(
        self,
        actor: dict,
        action: str,
        *,
        target_login: str | None = None,
        entity_type: str = "",
        entity_id: str | int = "",
        details: str = "",
    ) -> None:
        with self._connect() as db:
            db.execute(
                """INSERT INTO activity_log(
                    actor_email, actor_login, target_login, action, entity_type,
                    entity_id, details, created_at
                ) VALUES(?,?,?,?,?,?,?,?)""",
                (
                    str(actor.get("email", "") or ""),
                    str(actor.get("sub", "") or ""),
                    target_login or str(actor.get("sub", "") or ""),
                    action,
                    entity_type,
                    str(entity_id),
                    details[:1000],
                    datetime.now(UTC).isoformat(),
                ),
            )

    def history(self, target_login: str, limit: int = 200) -> list[ActivityLogItem]:
        with self._connect() as db:
            rows = db.execute(
                """SELECT id,actor_email,actor_login,target_login,action,entity_type,
                    entity_id,details,created_at FROM activity_log
                    WHERE target_login=? ORDER BY id DESC LIMIT ?""",
                (target_login, max(1, min(limit, 500))),
            ).fetchall()
        return [ActivityLogItem(**dict(row)) for row in rows]

    def sync_application(
        self,
        application: ApplicationSummary,
        metrolog_login: str,
        details: ApplicationDetails | None = None,
    ) -> None:
        address = application.address.strip()
        if not address:
            return
        now = datetime.now(UTC).isoformat()
        address_key = _text_key(address)
        client_name = application.client.strip() or "Не указан"
        client_key = _text_key(client_name)
        with self._connect() as db:
            db.execute(
                """INSERT INTO addresses(normalized_address,address,created_at,updated_at)
                VALUES(?,?,?,?) ON CONFLICT(normalized_address) DO UPDATE SET
                address=excluded.address, updated_at=excluded.updated_at""",
                (address_key, address, now, now),
            )
            address_id = int(db.execute(
                "SELECT id FROM addresses WHERE normalized_address=?", (address_key,)
            ).fetchone()["id"])
            db.execute(
                """INSERT INTO clients(address_id,normalized_name,name,created_at,updated_at)
                VALUES(?,?,?,?,?) ON CONFLICT(address_id,normalized_name) DO UPDATE SET
                name=excluded.name, updated_at=excluded.updated_at""",
                (address_id, client_key, client_name, now, now),
            )
            client_id = int(db.execute(
                "SELECT id FROM clients WHERE address_id=? AND normalized_name=?",
                (address_id, client_key),
            ).fetchone()["id"])
            for phone in (application.phone_number, application.phone_number_2):
                phone = phone.strip()
                phone_key = _phone_key(phone)
                if phone_key:
                    db.execute(
                        """INSERT INTO phones(client_id,normalized_phone,phone,created_at)
                        VALUES(?,?,?,?) ON CONFLICT(client_id,normalized_phone) DO UPDATE SET
                        phone=excluded.phone""",
                        (client_id, phone_key, phone, now),
                    )
            db.execute(
                """INSERT INTO source_applications(
                    application_id,address_id,client_id,metrolog_login,number,status,
                    work_date,updated_at
                ) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(application_id) DO UPDATE SET
                    address_id=excluded.address_id, client_id=excluded.client_id,
                    metrolog_login=excluded.metrolog_login, number=excluded.number,
                    status=excluded.status, work_date=excluded.work_date,
                    updated_at=excluded.updated_at""",
                (
                    application.id, address_id, client_id, metrolog_login,
                    application.number, application.status,
                    application.work_date.isoformat() if application.work_date else None, now,
                ),
            )
            if details is not None:
                for meter in details.water_meters:
                    identity = str(meter.id) if meter.id else _text_key(
                        "|".join((meter.device_kind, meter.serial_number, meter.registry_number))
                    )
                    db.execute(
                        """INSERT INTO meters(
                            address_id,source_meter_id,identity_key,device_kind,meter_type,
                            serial_number,registry_number,year,last_check,next_check,status,updated_at
                        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT(address_id,identity_key) DO UPDATE SET
                            source_meter_id=excluded.source_meter_id,
                            device_kind=excluded.device_kind,meter_type=excluded.meter_type,
                            serial_number=excluded.serial_number,
                            registry_number=excluded.registry_number,year=excluded.year,
                            last_check=excluded.last_check,next_check=excluded.next_check,
                            status=excluded.status,updated_at=excluded.updated_at""",
                        (
                            address_id, meter.id, identity, meter.device_kind, meter.meter_type,
                            meter.serial_number, meter.registry_number, meter.year,
                            meter.last_check, meter.next_check, meter.status, now,
                        ),
                    )

    def sync_many(self, applications: list[ApplicationSummary], metrolog_login: str) -> None:
        for application in applications:
            self.sync_application(application, metrolog_login)

    def addresses(self, query: str = "", limit: int = 200) -> list[RegistryAddress]:
        safe_limit = max(1, min(limit, 500))
        pattern = f"%{query.strip()}%"
        with self._connect() as db:
            address_rows = db.execute(
                """SELECT * FROM addresses WHERE address LIKE ? COLLATE NOCASE
                ORDER BY updated_at DESC LIMIT ?""",
                (pattern, safe_limit),
            ).fetchall()
            result: list[RegistryAddress] = []
            for address in address_rows:
                clients: list[RegistryClient] = []
                client_rows = db.execute(
                    "SELECT * FROM clients WHERE address_id=? ORDER BY name COLLATE NOCASE",
                    (address["id"],),
                ).fetchall()
                for client in client_rows:
                    phones = [RegistryPhone(**dict(row)) for row in db.execute(
                        "SELECT id,phone FROM phones WHERE client_id=? ORDER BY id",
                        (client["id"],),
                    ).fetchall()]
                    clients.append(RegistryClient(
                        id=client["id"], name=client["name"], phones=phones
                    ))
                meters = [RegistryMeter(**dict(row)) for row in db.execute(
                    """SELECT id,device_kind,meter_type,serial_number,registry_number,
                    year,last_check,next_check,status FROM meters WHERE address_id=? ORDER BY id""",
                    (address["id"],),
                ).fetchall()]
                sources = int(db.execute(
                    "SELECT COUNT(*) AS value FROM source_applications WHERE address_id=?",
                    (address["id"],),
                ).fetchone()["value"])
                result.append(RegistryAddress(
                    id=address["id"], address=address["address"], clients=clients,
                    meters=meters, applications_count=sources,
                    updated_at=address["updated_at"],
                ))
        return result

    def stats(self) -> RegistryStats:
        with self._connect() as db:
            def count(table: str) -> int:
                return int(db.execute(f"SELECT COUNT(*) AS value FROM {table}").fetchone()["value"])
            return RegistryStats(
                addresses=count("addresses"), clients=count("clients"),
                phones=count("phones"), meters=count("meters"),
                applications=count("source_applications"),
            )


operations_store = OperationsStore()
