# Build on Windows (no Android Studio)

1. **Extract All** files from the ZIP. Do not run from a ZIP preview.
2. Open the extracted `LeafTV` folder.
3. Double-click `BUILD-APK-WINDOWS.cmd`. It uses a cached portable JDK, Gradle 8.9, and SDK command-line tools.
4. After `BUILD SUCCESSFUL`, find `LeafTV-v6-one-screen-debug.apk` in the same folder.

If you're already in PowerShell in the extracted project folder, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\BUILD-APK-WINDOWS.ps1
```

If the script fails, share the build output. It does not announce success unless the APK exists.

To build **and** install directly on your already-connected TCL TV at `10.0.0.53`, use `BUILD-AND-INSTALL-TV.cmd` instead (Windows with ADB in `C:\platform-tools`). It checks the build/installation result and leaves the current HOME preference unchanged.
