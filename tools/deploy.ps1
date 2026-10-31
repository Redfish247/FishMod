# Bump, build the installed module only, copy into Skyblock mods. One shot for /deploy.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$mods = "$env:APPDATA\PrismLauncher\instances\Skyblock\minecraft\mods"

& powershell -NoProfile -File "$root\tools\check-game-closed.ps1" Skyblock
if ($LASTEXITCODE -ne 0) { Write-Output "ABORT: Skyblock is running, close it first"; exit 1 }

$old = Get-ChildItem $mods -Filter 'FishModDungeons-*.jar' | Where-Object { $_.Name -match '^FishModDungeons-(\d+)\.(\d+)\.(\d+)-(26\.[\d.]+)\.jar$' } | Select-Object -First 1
if (-not $old) { Write-Output "ABORT: no FishModDungeons jar in Skyblock mods"; exit 1 }
$null = $old.Name -match '^FishModDungeons-(\d+)\.(\d+)\.(\d+)-(26\.[\d.]+)\.jar$'
$ver = "$($Matches[1]).$($Matches[2]).$([int]$Matches[3] + 1)"
$suffix = $Matches[4]

git pull --ff-only -q
foreach ($m in '26.1.2', '26.2') {
    $p = "$root\dungeons-$m\gradle.properties"
    (Get-Content $p) -replace '^mod_version=.*', "mod_version=$ver-$m" | Set-Content $p -Encoding ascii
}

# Build only what gets installed; the auto-build hook covers the other module
& "$root\gradlew.bat" -q ":dungeons-${suffix}:build" --build-cache
if ($LASTEXITCODE -ne 0) { Write-Output "ABORT: build failed"; exit 1 }

$jar = "$root\dungeons-$suffix\build\libs\FishModDungeons-$ver-$suffix.jar"
& powershell -NoProfile -File "$root\tools\check-game-closed.ps1" Skyblock
if ($LASTEXITCODE -ne 0) { Write-Output "ABORT: Skyblock started during build"; exit 1 }
Copy-Item $jar $mods
Remove-Item $old.FullName
git commit -q --allow-empty -m "Ship $ver" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
Write-Output "DEPLOYED $ver-$suffix (replaced $($old.Name))"
