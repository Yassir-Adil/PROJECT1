<#
.SYNOPSIS
Run the local engineering gate without installing, starting, or controlling the phone.
.DESCRIPTION
Requires Python 3.11+ with research/requirements-eval.txt already installed,
JDK 17, Android SDK/build-tools 35.0.0, Git, and PowerShell 7.
Defaults to the existing local eval environment when present; never pip-installs.
Gradle is offline unless -AllowDependencyDownload is explicitly supplied.
Each run creates new logs/summary under ignored outputs/reports/quality-gate/.
#>
[CmdletBinding()]
param(
    [string]$Python,
    [string]$JavaHome,
    [string]$SdkRoot,
    [switch]$AllowDependencyDownload
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Run this script in PowerShell 7 (pwsh).' }
if (-not $Python) {
    $localEval = Join-Path $env:LOCALAPPDATA 'TriGuide-AI/eval-env/Scripts/python.exe'
    $Python = if (Test-Path -LiteralPath $localEval) { $localEval } else { (Get-Command python -ErrorAction Stop).Source }
}
$arguments = @((Join-Path $PSScriptRoot 'verify_local.py'))
if ($JavaHome) { $arguments += @('--java-home', $JavaHome) }
if ($SdkRoot) { $arguments += @('--sdk-root', $SdkRoot) }
if ($AllowDependencyDownload) { $arguments += '--allow-dependency-download' }
& $Python @arguments
exit $LASTEXITCODE
