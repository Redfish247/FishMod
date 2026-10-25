# Builds, installs into a FishTest instance, joins a singleplayer world, and fails on any FishMod/mixin error in the log.
# Usage: powershell -NoProfile -File tools/smoke-test.ps1 26.1.2|26.2 [world] [secondsInWorld]
param([Parameter(Mandatory)][ValidateSet('26.1.2','26.2')][string]$Ver, [string]$World = 'FishTestSurvival', [int]$Stay = 60,
      [ValidateSet('', 'on', 'off')][string]$Toggles = '')
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$inst = if ($Ver -eq '26.2') { 'FishTest-26.2' } else { 'FishTest' }
$mc = "$env:APPDATA\PrismLauncher\instances\$inst\minecraft"
$log = "$mc\logs\latest.log"
$running = { Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'javaw.exe' -and $_.CommandLine -match "instances[\\/]$([regex]::Escape($inst))[\\/]" } }
if (& $running) { Write-Output "$inst already running"; exit 2 }

& .\gradlew ":dungeons-$Ver`:build" -q
if ($LASTEXITCODE) { Write-Output "BUILD FAILED"; exit 1 }
$v = (Select-String "dungeons-$Ver\gradle.properties" -Pattern '^mod_version=(.*)').Matches[0].Groups[1].Value
Remove-Item "$mc\mods\FishModDungeons-*.jar" -ErrorAction SilentlyContinue
Copy-Item "dungeons-$Ver\build\libs\FishModDungeons-$v.jar" "$mc\mods\"
Remove-Item $log -ErrorAction SilentlyContinue
# -Toggles on/off: flip every boolean setting for this run, restored afterwards
$cfg = "$mc\config\fishmod-settings.json"
if ($Toggles) {
    Copy-Item $cfg "$cfg.smokebak" -Force
    $j = Get-Content $cfg -Raw | ConvertFrom-Json
    foreach ($pr in $j.PSObject.Properties) { if ($pr.Value -is [bool]) { $pr.Value = ($Toggles -eq 'on') } }
    $j | ConvertTo-Json -Depth 30 | Set-Content $cfg -Encoding utf8
}

$sw = [Diagnostics.Stopwatch]::StartNew()
Start-Process "$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe" -ArgumentList '-l', $inst, '-w', $World
$joined = $false
Add-Type 'using System; using System.Runtime.InteropServices; public class SmokeWin { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }'
while ($sw.Elapsed.TotalSeconds -lt 300) {
    Start-Sleep 5
    # dynamic_fps stalls loading while the window is unfocused
    & $running | ForEach-Object { [SmokeWin]::SetForegroundWindow((Get-Process -Id $_.ProcessId).MainWindowHandle) | Out-Null }
    if ((Test-Path $log) -and (Select-String $log -Pattern 'joined the game|logged in with entity id|\[CHAT\]' -Quiet)) { $joined = $true; break }
    if ((Test-Path $log) -and (Select-String $log -Pattern 'Crash report saved|#@!@# Game crashed' -Quiet)) { break }
}
$joinSec = [int]$sw.Elapsed.TotalSeconds
if ($joined) { Start-Sleep $Stay }
$p = & $running
$alive = [bool]$p
if ($p) { $p | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }
if ($Toggles) { Start-Sleep 2; Move-Item "$cfg.smokebak" $cfg -Force }

# only our own failures; other mods (SkyHanni etc.) log noise too
$bad = Select-String $log -Pattern 'fishmod|FishMod|betterpv|BetterPV|twitchbridge|Mixin apply failed|InvalidInjection|Game crashed' |
    Where-Object { $_.Line -notmatch 'Reloading ResourceManager' -and $_.Line -match 'Exception|ERROR|Error|failed|crash|\sat \S*fishmod\.' -and $_.Line -notmatch 'Failed to fetch' }
Write-Output "version=$v joined=$joined joinSec=$joinSec aliveAfter=$alive errors=$($bad.Count)"
$bad | Select-Object -First 30 | ForEach-Object { $_.Line.Substring(0, [Math]::Min(300, $_.Line.Length)) }
if (-not $joined -or -not $alive -or $bad.Count) { exit 1 }
exit 0
