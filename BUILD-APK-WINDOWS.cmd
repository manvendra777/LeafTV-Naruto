@echo off
setlocal EnableExtensions
set "PROJECT=%~dp0"
echo.
echo LeafTV v6 - Android APK Build ^(no Android Studio^)
echo.

REM Windows Explorer can open only one file from a ZIP in a temporary directory.
REM Fail clearly if the whole project was not extracted together.
if not exist "%PROJECT%settings.gradle" goto :extract
if not exist "%PROJECT%app\build.gradle" goto :extract
if not exist "%PROJECT%BUILD-APK-WINDOWS.ps1" goto :extract

cd /d "%PROJECT%"
if errorlevel 1 goto :folderError
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT%BUILD-APK-WINDOWS.ps1"
if errorlevel 1 goto :buildFailed

REM Never announce success unless the actual APK exists.
if not exist "%PROJECT%LeafTV-v6-one-screen-debug.apk" goto :missingApk

echo.
echo ===============================================
echo BUILD SUCCESSFUL: "%PROJECT%LeafTV-v6-one-screen-debug.apk"
echo ===============================================
echo.
pause
exit /b 0

:extract
echo ERROR: LeafTV build files are not together in this folder.
echo.
echo You are probably running this directly from the ZIP preview.
echo You MUST extract the whole ZIP first:
echo   1. Right-click the downloaded ZIP ^> Extract All...
echo   2. Open the extracted LeafTV folder ^(has settings.gradle^)
echo   3. Double-click BUILD-APK-WINDOWS.cmd in that folder.
echo.
echo Current location: "%PROJECT%"
echo No APK was built.
pause
exit /b 2

:folderError
echo ERROR: Could not enter the project directory. No APK was built.
pause
exit /b 3

:missingApk
echo ERROR: The build process returned success but no APK was created.
echo Expected: "%PROJECT%LeafTV-v6-one-screen-debug.apk"
echo No APK was built.
pause
exit /b 4

:buildFailed
echo.
echo ===============================================
echo BUILD FAILED. No APK was built.
echo Copy the actual PowerShell/Gradle error above.
echo ===============================================
echo.
pause
exit /b 1
