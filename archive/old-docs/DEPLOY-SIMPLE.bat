@echo off
REM Simple deployment script
REM Right-click this file and select "Run as administrator"

echo.
echo ========================================
echo   Portfolio Tracker - Deploy to Localhost
echo ========================================
echo.

REM Check if running as admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: This script must be run as Administrator!
    echo.
    echo Please:
    echo 1. Right-click this file
    echo 2. Select "Run as administrator"
    echo.
    pause
    exit /b 1
)

echo [OK] Running as Administrator
echo.

set TOMCAT_HOME=C:\Program Files\Apache Software Foundation\Tomcat 11.0
set WEBAPP_SOURCE=%~dp0webapp
set WEBAPP_DEST=%TOMCAT_HOME%\webapps\portfolio

REM Check Tomcat exists
if not exist "%TOMCAT_HOME%" (
    echo ERROR: Tomcat not found at %TOMCAT_HOME%
    pause
    exit /b 1
)

echo [OK] Found Tomcat
echo.

REM Stop Tomcat
echo [1/4] Stopping Tomcat...
net stop Tomcat11 >nul 2>&1
timeout /t 2 /nobreak >nul
echo [OK] Tomcat stopped
echo.

REM Remove old deployment
if exist "%WEBAPP_DEST%" (
    echo [2/4] Removing old deployment...
    rmdir /S /Q "%WEBAPP_DEST%"
    echo [OK] Old deployment removed
) else (
    echo [2/4] No old deployment found
)
echo.

REM Copy webapp
echo [3/4] Deploying application...
xcopy /E /I /Y "%WEBAPP_SOURCE%" "%WEBAPP_DEST%" >nul
if %errorLevel% neq 0 (
    echo ERROR: Failed to copy files
    pause
    exit /b 1
)
echo [OK] Application deployed
echo.

REM Start Tomcat
echo [4/4] Starting Tomcat...
net start Tomcat11
if %errorLevel% neq 0 (
    echo ERROR: Failed to start Tomcat
    pause
    exit /b 1
)
echo [OK] Tomcat started
echo.

REM Wait for startup
echo Waiting for Tomcat to initialize...
timeout /t 15 /nobreak
echo.

echo ========================================
echo   DEPLOYMENT SUCCESSFUL!
echo ========================================
echo.
echo Your portfolio tracker is now running!
echo.
echo Open your browser and visit:
echo http://localhost:8080/portfolio/portfolio?action=view
echo.
echo Or try:
echo http://localhost:8080/portfolio/
echo.
pause
