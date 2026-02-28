@echo off
echo ========================================
echo   Downloading Vosk Voice Recognition
echo ========================================
echo.

REM Create models folder
if not exist "models" mkdir models

echo [1/3] Downloading Vosk JAR (3 MB)...
powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/alphacephei/vosk/0.3.45/vosk-0.3.45.jar' -OutFile 'lib\vosk-0.3.45.jar'"
echo Done!
echo.

echo [2/3] Downloading JNA JAR (1.5 MB)...
powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar' -OutFile 'lib\jna-5.13.0.jar'"
echo Done!
echo.

echo [3/3] Downloading Indian English Model (40 MB)...
powershell -Command "Invoke-WebRequest -Uri 'https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip' -OutFile 'models\vosk-model-small-en-in-0.4.zip'"
echo Done!
echo.

echo Extracting model...
powershell -Command "Expand-Archive -Path 'models\vosk-model-small-en-in-0.4.zip' -DestinationPath 'models\' -Force"
echo Done!
echo.

echo ========================================
echo   Download Complete!
echo ========================================
echo.
echo Files downloaded:
echo   - lib\vosk-0.3.45.jar
echo   - lib\jna-5.13.0.jar
echo   - models\vosk-model-small-en-in-0.4\
echo.
echo Next: Tell Kiro "done" to add voice code!
echo.
pause
