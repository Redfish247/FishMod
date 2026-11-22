# Multibox UI test: launches FishTest (26.1.2), FishTest-26.2 and FishTest-26.3 singleplayer, tiles them, then runs the same steps on each.
# Usage: fmuitest.ps1 launch            -> start all, wait for world join, tile
#        fmuitest.ps1 run "<step>" ...  -> fmclick-style steps on every running instance; "shot <tag>" saves a screenshot per instance
param([Parameter(Position = 0)][string]$Mode, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Steps)
$insts = [ordered]@{ 'FishTest' = 'FishTestSurvival'; 'FishTest-26.2' = 'FishTestFlat'; 'FishTest-26.3' = 'FishTestFlat' }
$root = "$env:APPDATA\PrismLauncher\instances"
$out = "$env:TEMP\fmuitest"; New-Item -ItemType Directory -Force $out | Out-Null
function Get-Running($n) { Get-CimInstance Win32_Process | ? { $_.Name -eq 'javaw.exe' -and $_.CommandLine -match "instances[\\/]$([regex]::Escape($n))[\\/]" } | select -First 1 }
if ($Mode -eq 'launch') {
    foreach ($n in $insts.Keys) {
        if (Get-Running $n) { "$n already running"; continue }
        Remove-Item "$root\$n\minecraft\logs\latest.log" -ErrorAction SilentlyContinue
        Start-Process "$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe" -ArgumentList '-l', $n, '-w', $insts[$n]; Start-Sleep 6
    }
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
        $joined = @($insts.Keys | ? { $l = "$root\$_\minecraft\logs\latest.log"; (Test-Path $l) -and (Select-String -Path $l -Pattern 'joined the game' -Quiet) })
        if ($joined.Count -eq $insts.Count) { break }; Start-Sleep 5
    }
    foreach ($n in $insts.Keys) { $l = "$root\$n\minecraft\logs\latest.log"; "{0}: {1}" -f $n, $(if ((Test-Path $l) -and (Select-String -Path $l -Pattern 'joined the game' -Quiet)) { 'in world' } else { 'NOT in world' }) }
    & "$PSScriptRoot\fmtile.ps1"; exit 0
}
foreach ($n in $insts.Keys) {
    if (-not (Get-Running $n)) { "$n not running"; continue }
    $mc = "$root\$n\minecraft"; $log = "$mc\logs\latest.log"; $before = (Get-Content $log).Count
    $plain = @(); $shots = @()
    foreach ($s in $Steps) {
        if ($s -match '^shot (.+)$') {
            if ($plain) { & "$PSScriptRoot\fmclick.ps1" -Inst $n @plain | Out-Null; $plain = @() }
            & "$PSScriptRoot\fmclick.ps1" -Inst $n "key {F2}" "wait 1.5" | Out-Null
            $f = gci "$mc\screenshots" *.png | sort LastWriteTime | select -Last 1
            $dst = "$out\$($Matches[1])-$n.png"; Copy-Item $f.FullName $dst -Force; $shots += $dst
        } elseif ($s -match '^cmd (.+)$') {
            if ($plain) { & "$PSScriptRoot\fmclick.ps1" -Inst $n @plain | Out-Null; $plain = @() }
            & "$PSScriptRoot\fmclick.ps1" -Inst $n "key t" "wait 1" "type $($Matches[1])" "key {ENTER}" "wait 2" | Out-Null
        } else { $plain += $s }
    }
    if ($plain) { & "$PSScriptRoot\fmclick.ps1" -Inst $n @plain | Out-Null }
    "== $n"; $shots
    Get-Content $log | select -Skip $before | ? { $_ -match 'ERROR|Exception|FishDiag|Caused by' } | % { "  log: " + $_.Substring(0, [Math]::Min(220, $_.Length)) }
}
