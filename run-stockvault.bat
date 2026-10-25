@echo off
rem Double-click launcher. Normal mode by default; run "run-stockvault.bat -Diagnostic" for a disposable database.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-stockvault.ps1" %*
if errorlevel 1 pause
