# Launch TriGuide on the personal Redmi and print guidance and voice lines.
# Does not start an emulator. Does not decide whether the speaker was heard.
# Usage: .\scripts\test\redmi-smoke.ps1

param(
    [string]$Serial = "55aqpfqkvw9xbafe",
    [int]$Seconds = 8
)

$env:Path = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;" + $env:Path
$listed = & adb devices -l
if ($listed -notmatch [regex]::Escape($Serial) + "\s+device") {
    Write-Error "Redmi $Serial is not in state device. No emulator was started."
    exit 1
}

& adb -s $Serial logcat -c
& adb -s $Serial shell am force-stop com.triguide.ai
& adb -s $Serial shell am start -n com.triguide.ai/.MainActivity
Start-Sleep -Seconds $Seconds

Write-Host "---- version ----"
& adb -s $Serial shell dumpsys package com.triguide.ai | Select-String -Pattern "versionName=|versionCode=" | Select-Object -First 4
Write-Host "---- speak ----"
& adb -s $Serial logcat -d -s TriGuideVoice:I | Select-String -Pattern "speak result="
Write-Host "---- nav ----"
& adb -s $Serial logcat -d -s TriGuideNav:I
Write-Host "---- fatal ----"
& adb -s $Serial logcat -d -s AndroidRuntime:E | Select-String -Pattern "FATAL"
