<#
.SYNOPSIS
    Set ANDROID_HOME and put platform-tools on PATH for this PowerShell session.

.DESCRIPTION
    Finds the SDK at ANDROID_HOME when that path already contains adb, otherwise
    at %LOCALAPPDATA%\Android\Sdk. The change lasts for this session unless
    -Persist is passed.

    Permanent option (no admin). Pick one:

    1. From the repo root:
         .\scripts\setup\ensure-android-path.ps1 -Persist
       Writes ANDROID_HOME, ANDROID_SDK_ROOT, and platform-tools into the
       current user's environment. Open a new terminal. Restart Cursor so
       agents see the user PATH.

    2. Windows Settings, System, About, Advanced system settings,
       Environment Variables, under the user account:
         ANDROID_HOME = %LOCALAPPDATA%\Android\Sdk
         ANDROID_SDK_ROOT = the same folder
         Add %LOCALAPPDATA%\Android\Sdk\platform-tools to Path

    This script does not add the emulator directory. Device installs use the
    Redmi over USB.

.EXAMPLE
    .\scripts\setup\ensure-android-path.ps1

.EXAMPLE
    .\scripts\setup\ensure-android-path.ps1 -Persist
#>
[CmdletBinding()]
param(
    [switch]$Persist
)

$ErrorActionPreference = "Stop"

function Show-ErrorText {
    param([string]$Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
}

$sdk = $null
if ($env:ANDROID_HOME) {
    $candidateAdb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    if (Test-Path -LiteralPath $candidateAdb) {
        $sdk = $env:ANDROID_HOME
    }
}

if (-not $sdk) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$platformTools = Join-Path $sdk "platform-tools"
$adb = Join-Path $platformTools "adb.exe"
if (-not (Test-Path -LiteralPath $adb)) {
    Show-ErrorText "Android SDK platform-tools not found. Expected adb at: $adb"
    Write-Host "Install Android Studio or the command-line SDK, then run this script again."
    exit 1
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$normalizedTools = $platformTools.TrimEnd('\')
$alreadyOnPath = $false
foreach ($part in ($env:Path -split ';')) {
    if ($part.TrimEnd('\') -ieq $normalizedTools) {
        $alreadyOnPath = $true
        break
    }
}
if (-not $alreadyOnPath) {
    $env:Path = "$platformTools;$env:Path"
}

Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "platform-tools=$platformTools"
& $adb version
if ($LASTEXITCODE -ne 0) {
    Show-ErrorText "adb is present but 'adb version' failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}

if ($Persist) {
    try {
        [Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
        [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdk, "User")
        $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if (-not $userPath) {
            $userPath = ""
        }
        $alreadySaved = $false
        foreach ($part in ($userPath -split ';')) {
            if ($part.TrimEnd('\') -ieq $normalizedTools) {
                $alreadySaved = $true
                break
            }
        }
        if (-not $alreadySaved) {
            if ([string]::IsNullOrWhiteSpace($userPath)) {
                $userPath = $platformTools
            } else {
                $userPath = "$platformTools;$userPath"
            }
            [Environment]::SetEnvironmentVariable("Path", $userPath, "User")
        }
    } catch {
        Show-ErrorText "Could not save the user environment. $($_.Exception.Message)"
        exit 1
    }
    Write-Host "Saved ANDROID_HOME and platform-tools on the user PATH."
    Write-Host "Open a new terminal. Restart Cursor so agents inherit it."
} else {
    Write-Host "Session only. Permanent user PATH: .\scripts\setup\ensure-android-path.ps1 -Persist"
}

exit 0
