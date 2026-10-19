[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]{0,80}$')]
    [string]$SceneId,

    [ValidateRange(5, 300)]
    [int]$DurationSeconds = 20,

    [ValidateRange(0, 60)]
    [int]$StartupSeconds = 8,

    [string]$Serial = '55aqpfqkvw9xbafe',
    [string]$PackageName = 'com.triguide.ai',
    [string]$OutputRoot = ''
)

$ErrorActionPreference = 'Stop'

$repoDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoDir 'outputs\reports\redmi-evidence'
}

$adbPath = Join-Path ${env:LOCALAPPDATA} 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) {
    throw "ADB not found at $adbPath"
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    & $adbPath '-s' $Serial @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed with exit code ${LASTEXITCODE}: adb -s $Serial $($Arguments -join ' ')"
    }
}

function Get-AdbText {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = @(& $adbPath '-s' $Serial @Arguments 2>$null)
    return ($output -join "`n").Trim()
}

$deviceStateOutput = @(& $adbPath '-s' $Serial 'get-state' 2>$null)
$deviceState = ($deviceStateOutput -join "`n").Trim()
if ($deviceState -ne 'device') {
    $reportedState = if ([string]::IsNullOrWhiteSpace($deviceState)) { '<empty / serial unavailable>' } else { $deviceState }
    throw "BLOCKED: expected a ready physical Redmi device; state for $Serial is '$reportedState'. Run 'adb devices -l' and reconnect/authorize the phone before retrying."
}
if ($Serial -match '^emulator-') {
    throw 'This collector is phone-only and refuses emulator serials.'
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$captureDir = Join-Path $OutputRoot "$SceneId-$timestamp"
New-Item -ItemType Directory -Force -Path $captureDir | Out-Null

$metadataPath = Join-Path $captureDir 'metadata.txt'
@(
    "scene_id=$SceneId"
    "serial=$Serial"
    "package=$PackageName"
    "started_local=$((Get-Date).ToString('o'))"
    "duration_seconds=$DurationSeconds"
    "startup_seconds=$StartupSeconds"
    'mode=stationary_no_walking'
    'media_policy=local_only_no_video'
) | Set-Content -Encoding utf8 $metadataPath

Invoke-Adb @('shell', 'am', 'force-stop', $PackageName)
Invoke-Adb @('logcat', '-c')
Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
Invoke-Adb @('shell', 'am', 'start', '-W', '-n', "$PackageName/.MainActivity") | Out-File -Encoding utf8 (Join-Path $captureDir 'launch.txt')

Start-Sleep -Seconds $StartupSeconds
$focusDump = Get-AdbText @('shell', 'dumpsys', 'window', 'windows')
$activityDump = Get-AdbText @('shell', 'dumpsys', 'activity', 'activities')
$packagePattern = [regex]::Escape($PackageName)
$foreground =
    $focusDump -match "m(CurrentFocus|FocusedApp|FocusedWindow|TopFullscreenOpaqueWindowState)=.*$packagePattern" -or
    $activityDump -match "m(ResumedActivity|FocusedWindow|TopFullscreenOpaqueWindowState)=.*$packagePattern"
$keyguardVisible = $activityDump -match "isKeyguardShowing=true|KeyguardShowing=true"
if (-not $foreground -or $keyguardVisible) {
    throw "BLOCKED: the phone is locked or $PackageName is not in the foreground. Unlock the Redmi, keep TriGuide visible, and retry."
}
$startScreenshotPath = Join-Path $captureDir 'start.png'
& $adbPath '-s' $Serial 'exec-out' 'screencap' '-p' > $startScreenshotPath
if ($LASTEXITCODE -ne 0) { throw "Failed to capture $startScreenshotPath" }
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window.xml') | Out-Null
Invoke-Adb @('pull', '/sdcard/window.xml', (Join-Path $captureDir 'start-ui.xml')) | Out-Null
Invoke-Adb @('logcat', '-c')

Start-Sleep -Seconds $DurationSeconds

Invoke-Adb @('logcat', '-d', '-v', 'time', '-s', 'TriGuideNav:I', 'TriGuideVoice:I', 'TriGuideDetect:I', 'TriGuidePerf:I', '*:S') |
    Out-File -Encoding utf8 (Join-Path $captureDir 'filtered-logcat.txt')
$endScreenshotPath = Join-Path $captureDir 'end.png'
& $adbPath '-s' $Serial 'exec-out' 'screencap' '-p' > $endScreenshotPath
if ($LASTEXITCODE -ne 0) { throw "Failed to capture $endScreenshotPath" }
Invoke-Adb @('shell', 'dumpsys', 'meminfo', $PackageName) | Out-File -Encoding utf8 (Join-Path $captureDir 'meminfo.txt')
Invoke-Adb @('shell', 'dumpsys', 'battery') | Out-File -Encoding utf8 (Join-Path $captureDir 'battery.txt')
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window.xml') | Out-Null
Invoke-Adb @('pull', '/sdcard/window.xml', (Join-Path $captureDir 'end-ui.xml')) | Out-Null

$logPath = Join-Path $captureDir 'filtered-logcat.txt'
$logLines = @(Get-Content -LiteralPath $logPath)
$latencies = @()
foreach ($line in $logLines) {
    if ($line -match 'TriGuidePerf.*latencyMs=([0-9]+(?:\.[0-9]+)?)') {
        $latencies += [double]$Matches[1]
    }
}
$sortedLatencies = @($latencies | Sort-Object)

function Get-NearestRank {
    param([array]$Values, [double]$Percentile)
    if ($Values.Count -eq 0) { return '' }
    $index = [Math]::Max(0, [Math]::Ceiling($Percentile * $Values.Count) - 1)
    return $Values[$index]
}

$navLines = @($logLines | Where-Object { $_ -match 'TriGuideNav' })
$holdCount = @($navLines | Where-Object { $_ -match 'action=HOLD' }).Count
$unknownCount = @($navLines | Where-Object { $_ -match 'action=UNKNOWN' }).Count
$clearOrTurnCount = @($navLines | Where-Object {
    $_ -match 'CLEAR_OBSERVED|risk=CLEAR|action=STRAIGHT|action=TURN|action=LEFT|action=RIGHT'
}).Count
$voiceLines = @($logLines | Where-Object { $_ -match 'TriGuideVoice' })
$voiceRequests = @($voiceLines | Where-Object { $_ -match 'speak result=' })
$voiceSkips = @($voiceLines | Where-Object { $_ -match 'skipped ' })
$fps = if ($DurationSeconds -gt 0 -and $latencies.Count -gt 0) {
    [Math]::Round($latencies.Count / $DurationSeconds, 2)
} else {
    0
}

$summaryPath = Join-Path $captureDir 'summary.tsv'
$tab = [char]9
@(
    "scene_id${tab}samples${tab}fps_approx${tab}latency_p50_ms${tab}latency_p90_ms${tab}nav_hold_log_lines${tab}nav_unknown_log_lines${tab}clear_or_turn_log_lines${tab}voice_request_log_lines${tab}voice_skip_log_lines${tab}voice_log_lines${tab}note"
    "$SceneId${tab}$($latencies.Count)${tab}$fps${tab}$(Get-NearestRank $sortedLatencies 0.50)${tab}$(Get-NearestRank $sortedLatencies 0.90)${tab}$holdCount${tab}$unknownCount${tab}$clearOrTurnCount${tab}$($voiceRequests.Count)${tab}$($voiceSkips.Count)${tab}$($voiceLines.Count)${tab}Diagnostic logs only; no audible-output claim."
) | Set-Content -Encoding utf8 $summaryPath

Write-Output "CAPTURE_DIR=$captureDir"
Write-Output "SUMMARY=$summaryPath"
Write-Output "DETECTOR_SAMPLES=$($latencies.Count) FPS_APPROX=$fps LATENCY_P50_MS=$(Get-NearestRank $sortedLatencies 0.50) LATENCY_P90_MS=$(Get-NearestRank $sortedLatencies 0.90)"
Write-Output "NAV_HOLD_LOG_LINES=$holdCount NAV_UNKNOWN_LOG_LINES=$unknownCount CLEAR_OR_TURN_LOG_LINES=$clearOrTurnCount VOICE_REQUEST_LOG_LINES=$($voiceRequests.Count) VOICE_SKIP_LOG_LINES=$($voiceSkips.Count) VOICE_LOG_LINES=$($voiceLines.Count)"
