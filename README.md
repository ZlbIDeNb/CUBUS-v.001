# CUBUS Runtime

CUBUS Runtime is the core software platform for managing a metrology service.
The Android client supports field employees, while the FastAPI server provides
authentication, CRM integration, administration, reporting, inventory and
application statistics.

## Repository layout

- `android/` — Android application built with Kotlin and Jetpack Compose.
- `backend/` — FastAPI server, CRM integration and automated tests.
- `backend/deploy/` — systemd and Caddy deployment configuration.

Generated APK files, deployment archives, databases, uploaded documents,
credentials and local SDK files are deliberately excluded from Git.

## Backend development

1. Create and activate a Python virtual environment.
2. Install the dependencies:

   ```bash
   pip install -r backend/requirements.txt
   ```

3. Copy `backend/.env.example` to `backend/.env` and provide local values.
   Never commit `backend/.env`.
4. Run the tests from `backend/`:

   ```bash
   python -m pytest -q
   ```

5. Start the API:

   ```bash
   uvicorn app.main:app --host 127.0.0.1 --port 8000
   ```

## Android development

The Android project requires JDK 17 and Android SDK 35. Set the local SDK path
in `android/local.properties`, then build from `android/`:

```bash
./gradlew assembleDebug
```

The production API base URL is configured through the `API_BASE_URL` Gradle
property and defaults to `https://api.cubus.pro/`.

## Security

Never commit API tokens, passwords, signing keys, production `.env` files,
SQLite databases, APK files or deployment archives. If a credential is ever
committed, revoke it immediately; deleting it in a later commit is not enough.

## GitHub backup

Run `BACKUP_CUBUS_TO_GITHUB.cmd` after a completed update. The backup process:

- checks GitHub for newer changes before touching the repository;
- stages only approved source-code and documentation paths;
- refuses to commit production secrets or generated runtime data;
- creates a dated commit and pushes it to the `main` branch.

The current server update script invokes the same backup automatically after a
successful deployment. If the server deployment fails, no backup commit is
created.
