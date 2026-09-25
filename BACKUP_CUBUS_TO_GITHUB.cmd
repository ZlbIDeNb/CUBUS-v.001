@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\backup-to-github.ps1"
if errorlevel 1 (
  echo.
  echo CUBUS Runtime backup failed. Review the error above.
) else (
  echo.
  echo CUBUS Runtime backup completed.
)
pause

