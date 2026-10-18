# TriGuide local GPT bridge (OpenAI API -> docs/BRIDGE_FROM_GPT.md)
param(
    [string]$Note = "",
    [string]$Model = "",
    [switch]$DryRun
)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $repo

$envFile = Join-Path $repo ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) { return }
        $pair = $line.Split("=", 2)
        $name = $pair[0].Trim()
        $value = $pair[1].Trim().Trim('"').Trim("'")
        if ($name) { Set-Item -Path "Env:$name" -Value $value }
    }
}

$py = @("scripts/bridge/run_gpt_bridge.py")
if ($DryRun) { $py += "--dry-run" }
if ($Model) { $py += @("--model", $Model) }
if ($Note) { $py += @("--note", $Note) }

python @py
exit $LASTEXITCODE
