# Stop hook: build dungeons-26.1.2 when sources are newer than the newest jar. Never deploys (that is /ship).
# Exit 0 = nothing to do or build ok. Exit 2 = build failed (output goes back to Claude so it can fix).
$raw = [Console]::In.ReadToEnd()
try { if ((ConvertFrom-Json $raw).stop_hook_active) { exit 0 } } catch {}

$root = Split-Path -Parent $PSScriptRoot
$src = Join-Path $root 'dungeons-26.1.2\src'
$libs = Join-Path $root 'dungeons-26.1.2\build\libs'

$newestSrc = Get-ChildItem $src -Recurse -File -Include *.kt, *.java, *.json | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$newestJar = Get-ChildItem $libs -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|dev' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($newestJar -and $newestSrc -and $newestJar.LastWriteTime -ge $newestSrc.LastWriteTime) { exit 0 }

$created = $false
$lock = New-Object System.Threading.Mutex($true, 'Global\fishmod-auto-build', [ref]$created)
if (-not $created) { Write-Output 'auto-build: another build is running, skipped'; exit 0 }
try {
    Push-Location $root
    $out = & .\gradlew.bat ':dungeons-26.1.2:build' -q 2>&1 | Out-String
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) {
        [Console]::Error.WriteLine("auto-build FAILED (not deployed):`n" + (($out -split "`n") | Select-Object -Last 40) -join "`n")
        exit 2
    }
    Write-Output 'auto-build ok (not deployed; run /ship to deploy)'
    exit 0
} finally { $lock.ReleaseMutex() }
