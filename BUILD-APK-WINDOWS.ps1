# LeafTV — portable Windows APK build. No Android Studio and no administrator access required.
# Dependencies are cached in %LOCALAPPDATA%\LeafTVBuildTools.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($project -match '(?i)\\AppData\\Local\\Temp\\') { Write-Warning 'This script appears to be running from a temporary ZIP preview. Use Extract All on the ZIP first.' }
if (-not (Test-Path (Join-Path $project 'settings.gradle'))) {
    throw "This script must be inside the LeafTV Android project (next to settings.gradle)."
}
if (-not $env:LOCALAPPDATA) { throw 'LOCALAPPDATA is not set; run on a Windows account in PowerShell.' }
$cache = Join-Path $env:LOCALAPPDATA 'LeafTVBuildTools'
$jdkDir = Join-Path $cache 'jdk-17'
$gradleDir = Join-Path $cache 'gradle-8.9'
$sdk = Join-Path $cache 'android-sdk'
New-Item -Force -ItemType Directory -Path $cache | Out-Null

function Fetch-Zip([string]$url, [string]$zip, [string]$extractDir) {
    if (-not (Test-Path $extractDir)) {
        Write-Host "Downloading $url" -ForegroundColor Cyan
        if (-not (Test-Path $zip)) {
            Invoke-WebRequest -Uri $url -OutFile $zip -MaximumRedirection 10
        }
        New-Item -Force -ItemType Directory -Path $extractDir | Out-Null
        Write-Host "Extracting $(Split-Path $zip -Leaf)..."
        Expand-Archive -LiteralPath $zip -DestinationPath $extractDir -Force
    }
}

# JDK 17 portable archive via Adoptium (download only, not system-installed).
$jdkStage = Join-Path $cache 'jdk-download'
if (-not (Test-Path (Join-Path $jdkDir 'bin\java.exe'))) {
    Fetch-Zip 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' (Join-Path $cache 'jdk17.zip') $jdkStage
    $jdkFound = Get-ChildItem $jdkStage -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if (-not $jdkFound) { throw 'JDK zip did not contain expected Java files.' }
    if (Test-Path $jdkDir) { Remove-Item $jdkDir -Recurse -Force }
    Move-Item -LiteralPath $jdkFound.FullName -Destination $jdkDir
}
$env:JAVA_HOME = $jdkDir
$env:PATH = "$(Join-Path $jdkDir 'bin');$env:PATH"
Write-Host 'Java:'
& (Join-Path $jdkDir 'bin\java.exe') -version

# Install Google's current Windows Android SDK CLI tools into the user profile.
$cliRoot = Join-Path $sdk 'cmdline-tools\latest'
$sdkManager = Join-Path $cliRoot 'bin\sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
    $cliStage = Join-Path $cache 'android-cli-download'
    Fetch-Zip 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' (Join-Path $cache 'android-cli.zip') $cliStage
    $unpacked = Join-Path $cliStage 'cmdline-tools'
    if (-not (Test-Path (Join-Path $unpacked 'bin\sdkmanager.bat'))) { throw 'Android CLI archive format was unexpected.' }
    New-Item -Force -ItemType Directory -Path (Split-Path $cliRoot -Parent) | Out-Null
    if (Test-Path $cliRoot) { Remove-Item $cliRoot -Recurse -Force }
    Move-Item -LiteralPath $unpacked -Destination $cliRoot
}
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
Write-Host 'Accepting Android SDK licenses (SDK only, no Android Studio)...'
@(1..30 | ForEach-Object { 'y' }) | & $sdkManager --sdk_root="$sdk" --licenses | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Android SDK license step failed.' }
Write-Host 'Installing Android SDK API 35 and Build Tools 35...'
# Note: the Gradle and Android SDK downloads are required only for the first build.
& $sdkManager --sdk_root="$sdk" 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK components could not be installed.' }

# Gradle 8.9 is the compatible version for Android Gradle Plugin 8.7.3.
$gradle = Join-Path $gradleDir 'bin\gradle.bat'
if (-not (Test-Path $gradle)) {
    $gradleStage = Join-Path $cache 'gradle-download'
    Fetch-Zip 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' (Join-Path $cache 'gradle-8.9-bin.zip') $gradleStage
    $unpacked = Join-Path $gradleStage 'gradle-8.9'
    if (-not (Test-Path (Join-Path $unpacked 'bin\gradle.bat'))) { throw 'Gradle archive format was unexpected.' }
    if (Test-Path $gradleDir) { Remove-Item $gradleDir -Recurse -Force }
    Move-Item -LiteralPath $unpacked -Destination $gradleDir
}
Set-Location $project
Write-Host 'Building LeafTV debug APK...' -ForegroundColor Cyan
& $gradle --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'LeafTV APK compilation failed. Please share the error output.' }
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw 'Gradle returned success but the APK was not found.' }
$dest = Join-Path $project 'LeafTV-v6-one-screen-debug.apk'
Copy-Item -LiteralPath $apk -Destination $dest -Force
Write-Host "BUILD SUCCESSFUL: $dest" -ForegroundColor Green
Write-Host 'Install with: C:\platform-tools\adb.exe install -r "' $dest '"'
Write-Host 'Do not disable the stock TV launcher before testing the APK.'
