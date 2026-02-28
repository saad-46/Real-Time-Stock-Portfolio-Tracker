@echo off
echo ========================================
echo   Downloading Vosk Language Model
echo ========================================
echo.
echo This will download the Indian English model (40 MB)
echo.
pause

echo Downloading model...
powershell -Command "Invoke-WebRequest -Uri 'https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip' -OutFile 'models\vosk-model-small-en-in-0.4.zip'"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Download failed!
    echo Please download manually from:
    echo https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip
    echo.
    pause
    exit /b 1
)

echo.
echo Extracting model...
powershell -Command "Expand-Archive -Path 'models\vosk-model-small-en-in-0.4.zip' -DestinationPath 'models\' -Force"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Extraction failed!
    echo Please extract manually.
    echo.
    pause
    exit /b 1
)

echo.
echo Cleaning up...
del "models\vosk-model-small-en-in-0.4.zip"

echo.
echo ========================================
echo   SUCCESS!
echo ========================================
echo.
echo Model installed at:
echo   models\vosk-model-small-en-in-0.4\
echo.
echo Next: Tell Kiro "model downloaded"
echo.
pause
