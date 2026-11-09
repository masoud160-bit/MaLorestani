@echo off
setlocal
cd /d "%~dp0"
echo Checking requirements and building the Android APK...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-debug-apk.ps1"
set "assetExitCode=%ERRORLEVEL%"
if not "%assetExitCode%"=="0" (
  echo.
  echo BUILD FAILED. Read the error above and send it for troubleshooting.
) else (
  echo.
  echo BUILD COMPLETED. The installable APK is inside the dist folder.
)
echo.
pause
exit /b %assetExitCode%
