@echo off
echo.
echo ========================================
echo   FULL REDEPLOY - Portfolio Tracker
echo ========================================
echo.

REM Check if running as admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: Must run as Administrator!
    echo Right-click and select "Run as administrator"
    pause
    exit /b 1
)

set TOMCAT_HOME=C:\Program Files\Apache Software Foundation\Tomcat 11.0
set WEBAPP_SOURCE=%~dp0webapp
set WEBAPP_DEST=%TOMCAT_HOME%\webapps\portfolio

echo [1/5] Stopping Tomcat...
net stop Tomcat11 >nul 2>&1
timeout /t 3 /nobreak >nul
echo Done.

echo [2/5] Clearing old deployment...
if exist "%WEBAPP_DEST%" (
    rmdir /S /Q "%WEBAPP_DEST%"
)
if exist "%TOMCAT_HOME%\work\Catalina" (
    rmdir /S /Q "%TOMCAT_HOME%\work\Catalina"
)
echo Done.

echo [3/5] Copying new files...
xcopy /E /I /Y "%WEBAPP_SOURCE%" "%WEBAPP_DEST%" >nul
echo Done.

echo [4/5] Starting Tomcat...
net start Tomcat11
timeout /t 5 /nobreak >nul
echo Done.

echo [5/5] Waiting for startup...
timeout /t 15 /nobreak
echo Done.

echo.
echo ========================================
echo   DEPLOYMENT COMPLETE!
echo ========================================
echo.
echo Your app is ready at:
echo http://localhost:8080/portfolio/portfolio?action=dashboard
echo.
echo IMPORTANT: Clear your browser cache!
echo Press Ctrl+F5 in your browser to force refresh
echo.
pause
