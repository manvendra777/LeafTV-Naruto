# Backward-compatible entry point. Uses the full portable builder.
# Neither Android Studio nor preinstalled Gradle is required.
$ErrorActionPreference = 'Stop'
$builder = Join-Path $PSScriptRoot 'BUILD-APK-WINDOWS.ps1'
if (-not (Test-Path -LiteralPath $builder)) { throw 'Missing BUILD-APK-WINDOWS.ps1 from the project. Re-extract the full LeafTV ZIP.' }
& $builder
if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
