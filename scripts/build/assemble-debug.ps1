<#
.SYNOPSIS
    Build the debug APK with JDK 17 and copy it to outputs/apk.

.DESCRIPTION
    Sets JAVA_HOME to a JDK 17 for this session, ensures ANDROID_HOME, runs
    :app:assembleDebug, and copies the APK to
    outputs/apk/TriGuideAI-<versionName>-debug.apk.
    versionName is read from android-app/app/build.gradle.kts.

    --no-daemon is intentional so Gradle uses this session's JAVA_HOME
    instead of a daemon started under a different JDK.

.EXAMPLE
    .\scripts\build\assemble-debug.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

function Show-ErrorText {
    param([string]$Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
}

function Test-Jdk17Home {
    param([string]$JdkHome)
    if ([string]::IsNullOrWhiteSpace($JdkHome)) {
        return $false
    }
    $javaExe = Join-Path $JdkHome "bin\java.exe"
    $releaseFile = Join-Path $JdkHome "release"
    if (-not (Test-Path -LiteralPath $javaExe)) {
        return $false
    }
    if (-not (Test-Path -LiteralPath $releaseFile)) {
        return $false
    }
    $releaseText = [System.IO.File]::ReadAllText($releaseFile)
    return $releaseText -match 'JAVA_VERSION="17[\."]'
}

function Find-Jdk17 {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) {
        $candidates.Add($env:JAVA_HOME)
    }

    $roots = New-Object System.Collections.Generic.List[string]
    if ($env:LOCALAPPDATA) {
        $roots.Add((Join-Path $env:LOCALAPPDATA "Java"))
    }
    if ($env:ProgramFiles) {
        $roots.Add((Join-Path $env:ProgramFiles "Eclipse Adoptium"))
        $roots.Add((Join-Path $env:ProgramFiles "Java"))
        $roots.Add((Join-Path $env:ProgramFiles "Microsoft"))
    }
    $programFilesX86 = ${env:ProgramFiles(x86)}
    if ($programFilesX86) {
        $roots.Add((Join-Path $programFilesX86 "Eclipse Adoptium"))
        $roots.Add((Join-Path $programFilesX86 "Java"))
    }

    foreach ($root in $roots) {
        if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path -LiteralPath $root)) {
            continue
        }
        $dirs = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^(jdk-17|microsoft-jdk-17)' } |
            Sort-Object Name -Descending
        foreach ($dir in $dirs) {
            $candidates.Add($dir.FullName)
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-Jdk17Home -JdkHome $candidate) {
            return $candidate
        }
    }
    return $null
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$setupScript = Join-Path $repoRoot "scripts\setup\ensure-android-path.ps1"
& $setupScript
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$jdkHome = Find-Jdk17
if (-not $jdkHome) {
    Show-ErrorText "JDK 17 not found. Install Temurin 17 (or another JDK 17) and rerun."
    Write-Host "Looked at JAVA_HOME, %LOCALAPPDATA%\Java, Eclipse Adoptium, Program Files\Java, and Microsoft JDK."
    exit 1
}

$env:JAVA_HOME = $jdkHome
$jdkBin = Join-Path $jdkHome "bin"
$firstPath = (($env:Path -split ';') | Where-Object { $_ -ne "" } | Select-Object -First 1)
if (-not $firstPath -or ($firstPath.TrimEnd('\') -ine $jdkBin.TrimEnd('\'))) {
    $env:Path = "$jdkBin;$env:Path"
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
& (Join-Path $jdkBin "java.exe") -version
if ($LASTEXITCODE -ne 0) {
    Show-ErrorText "java -version failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}

$gradleFile = Join-Path $repoRoot "android-app\app\build.gradle.kts"
if (-not (Test-Path -LiteralPath $gradleFile)) {
    Show-ErrorText "Gradle file not found: $gradleFile"
    exit 1
}

$gradleText = [System.IO.File]::ReadAllText($gradleFile)
$versionMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
if (-not $versionMatch.Success) {
    Show-ErrorText "Could not read versionName from $gradleFile"
    exit 1
}
$versionName = $versionMatch.Groups[1].Value
if ($versionName -notmatch '^[0-9A-Za-z][0-9A-Za-z\.\-_]*$') {
    Show-ErrorText "versionName '$versionName' cannot be used in an APK file name."
    exit 1
}

$gradlew = Join-Path $repoRoot "android-app\gradlew.bat"
if (-not (Test-Path -LiteralPath $gradlew)) {
    Show-ErrorText "Gradle wrapper not found: $gradlew"
    exit 1
}

$androidDir = Join-Path $repoRoot "android-app"
Write-Host "assembleDebug versionName=$versionName"
Push-Location $androidDir
try {
    & $gradlew ":app:assembleDebug" "--no-daemon"
    $gradleCode = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($gradleCode -ne 0) {
    Show-ErrorText "assembleDebug failed with exit code $gradleCode."
    exit $gradleCode
}

$builtApk = Join-Path $repoRoot "android-app\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $builtApk)) {
    Show-ErrorText "Build reported success but the APK is missing: $builtApk"
    exit 1
}

$destDir = Join-Path $repoRoot "outputs\apk"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$destApk = Join-Path $destDir "TriGuideAI-$versionName-debug.apk"
Copy-Item -LiteralPath $builtApk -Destination $destApk -Force

$bytes = (Get-Item -LiteralPath $destApk).Length
Write-Host "APK $destApk ($bytes bytes)"
exit 0
