<#
.SYNOPSIS
    Install the latest debug APK on the project Redmi only.

.DESCRIPTION
    Serial is fixed: 55aqpfqkvw9xbafe. The script never picks another device
    or an emulator. It fails if that serial is missing, unauthorized, or not
    in the 'device' state.

    The APK is the newest outputs/apk/TriGuideAI-*-debug.apk by last write time.
    Build one first with scripts/build/assemble-debug.ps1.

.EXAMPLE
    .\scripts\test\redmi-install.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$Serial = "55aqpfqkvw9xbafe"
$PackageName = "com.triguide.ai"

function Show-ErrorText {
    param([string]$Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$setupScript = Join-Path $repoRoot "scripts\setup\ensure-android-path.ps1"
& $setupScript
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$adbExe = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adbExe)) {
    Show-ErrorText "adb is not available after SDK setup. Expected: $adbExe"
    exit 1
}

$deviceOutput = @(& $adbExe devices)
$adbCode = $LASTEXITCODE
foreach ($line in $deviceOutput) {
    Write-Host $line
}
if ($adbCode -ne 0) {
    Show-ErrorText "adb devices failed with exit code $adbCode."
    exit $adbCode
}

$state = $null
foreach ($line in @($deviceOutput)) {
    $escaped = [regex]::Escape($Serial)
    if ($line -match "^$escaped\s+(\S+)\s*$") {
        $state = $Matches[1]
        break
    }
}

if (-not $state) {
    Show-ErrorText "Redmi $Serial is not listed by adb."
    Write-Host "Plug in the phone, enable USB debugging, and accept the trust prompt."
    Write-Host "This script installs only on $Serial. It will not use another phone or an emulator."
    exit 1
}

if ($state -ne "device") {
    Show-ErrorText "Redmi $Serial is '$state', not 'device'."
    Write-Host "Unlock the phone and accept the USB debugging prompt, then run this script again."
    exit 1
}

$apkDir = Join-Path $repoRoot "outputs\apk"
if (-not (Test-Path -LiteralPath $apkDir)) {
    Show-ErrorText "No APK folder at $apkDir. Run .\scripts\build\assemble-debug.ps1 first."
    exit 1
}

$apk = Get-ChildItem -LiteralPath $apkDir -Filter "TriGuideAI-*-debug.apk" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) {
    Show-ErrorText "No TriGuideAI-*-debug.apk in $apkDir. Run .\scripts\build\assemble-debug.ps1 first."
    exit 1
}

Write-Host "Installing $($apk.FullName) on $Serial only."
& $adbExe -s $Serial install -r $apk.FullName
if ($LASTEXITCODE -ne 0) {
    Show-ErrorText "adb install failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}

Write-Host "Installed $($apk.Name) on $Serial."
$pkgLines = & $adbExe -s $Serial shell dumpsys package $PackageName
$versionLine = $pkgLines | Select-String -Pattern "versionName=" | Select-Object -First 1
if ($versionLine) {
    Write-Host ($versionLine.Line.Trim())
}
exit 0
