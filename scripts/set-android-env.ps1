# Prefer scripts/setup/ensure-android-path.ps1 (session ANDROID_HOME + platform-tools, optional -Persist).
# This older helper also prepends the emulator directory when that folder exists.
# Run:  .\scripts\set-android-env.ps1
# Persist: .\scripts\set-android-env.ps1 -Persist

param(
    [switch]$Persist
)

$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
if (-not (Test-Path $sdk)) {
    Write-Error "Android SDK not found at $sdk. Install Android Studio or SDK packages first."
    exit 1
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$platformTools = Join-Path $sdk "platform-tools"
$emulator = Join-Path $sdk "emulator"
$pathAdd = @($platformTools, $emulator) | Where-Object { Test-Path $_ }
$env:Path = ($pathAdd -join ";") + ";" + $env:Path

Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
if (Get-Command adb -ErrorAction SilentlyContinue) {
    adb version
} else {
    Write-Warning "adb still not found on PATH for this session."
}

if ($Persist) {
    [Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
    [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdk, "User")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    foreach ($p in $pathAdd) {
        if ($userPath -notlike "*$p*") {
            $userPath = "$p;$userPath"
        }
    }
    [Environment]::SetEnvironmentVariable("Path", $userPath, "User")
    Write-Host "Persisted ANDROID_HOME and platform-tools to User environment. Restart terminals/Cursor to pick up PATH."
}
