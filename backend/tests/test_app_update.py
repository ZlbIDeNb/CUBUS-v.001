import hashlib
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import app


class AppUpdateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.apk = Path(self.temp.name) / "CUBUS-latest.apk"
        self.content = b"test-apk-content"
        self.apk.write_bytes(self.content)
        self.settings = Settings(
            app_secret="x" * 32,
            client_base_url="https://example.test/api",
            client_base_token="token",
            update_apk_path=str(self.apk),
            update_version_code=6,
            update_version_name="1.05",
            update_release_notes="Автоматическое обновление",
        )
        app.dependency_overrides[get_settings] = lambda: self.settings
        self.client = TestClient(app)

    def tearDown(self):
        app.dependency_overrides.clear()
        self.temp.cleanup()

    def test_reports_newer_version_and_checksum(self):
        response = self.client.get("/api/v1/app-update", params={"version_code": 5})
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["update_available"])
        self.assertEqual(payload["version_code"], 6)
        self.assertEqual(payload["sha256"], hashlib.sha256(self.content).hexdigest())
        self.assertEqual(payload["file_size"], len(self.content))

    def test_current_version_does_not_request_update(self):
        response = self.client.get("/api/v1/app-update", params={"version_code": 6})
        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.json()["update_available"])

    def test_download_returns_apk(self):
        response = self.client.get("/api/v1/app-update/apk")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.content, self.content)
        self.assertEqual(
            response.headers["content-type"],
            "application/vnd.android.package-archive",
        )


if __name__ == "__main__":
    unittest.main()
