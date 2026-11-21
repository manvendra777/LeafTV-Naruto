@echo off
setlocal EnableExtensions
set "PROJECT=%~dp0"
set "APK=%PROJECT%LeafTV-v6-one-screen-debug.apk"
echo.
echo LeafTV v6 - build, install on TCL TV and launch
if not exist "%PROJECT%settings.gradle" goto :extract
if not exist "%PROJECT%BUILD-APK-WINDOWS.ps1" goto :extract
cd /d "%PROJECT%"
if errorlevel 1 goto :failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT%BUILD-APK-WINDOWS.ps1"
if errorlevel 1 goto :failed
if not exist "%APK%" goto :noapk
set "ADB=C:\platform-tools\adb.exe"
if not exist "%ADB%" (
  where adb.exe >nul 2>nul
  if errorlevel 1 goto :noadb
  set "ADB=adb.exe"
)
"%ADB%" connect 10.0.0.53:5555
"%ADB%" -s 10.0.0.53:5555 get-state
if errorlevel 1 goto :nodevice
"%ADB%" -s 10.0.0.53:5555 install -r "%APK%"
if errorlevel 1 goto :installfailed
"%ADB%" -s 10.0.0.53:5555 shell am start -n com.leaftv.launcher/.MainActivity
if errorlevel 1 goto :launchfailed
echo.
echo BUILD / INSTALL / LAUNCH COMPLETED. Test the wallpaper library with the remote.
echo The existing default HOME setting is unchanged.
pause
exit /b 0
:extract
echo ERROR: Extract All first; build scripts and project files must be in one folder.
goto :endfail
:noapk
echo ERROR: No APK generated. No installation was attempted.
goto :endfail
:noadb
echo ERROR: ADB not found. Build succeeded; install "%APK%" manually.
goto :endfail
:nodevice
echo ERROR: TV not connected at 10.0.0.53:5555; check its current IP.
goto :endfail
:installfailed
echo ERROR: APK build succeeded but ADB installation failed.
goto :endfail
:launchfailed
echo ERROR: APK installed but couldn't launch. Check the ADB error above.
goto :endfail
:failed
echo ERROR: APK build failed. Check the actual PowerShell error above.
:endfail
pause
exit /b 1
