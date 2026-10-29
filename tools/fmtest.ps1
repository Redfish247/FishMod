# Sends chat commands to a running FishTest window (singleplayer only), then prints new chat and error lines from its log.
# Usage: powershell -NoProfile -File tools/fmtest.ps1 [-Inst FishTest|FishTest-26.2] "/badev skyblock on" "/badev location set DWARVEN_MINES" "@shot" "@wait 5"
# "@shot" presses F2 and prints the screenshot path; "@wait N" sleeps N seconds; "@key X" sends raw SendKeys X (e.g. "{ESC}").
param([string]$Inst = 'FishTest-26.2', [Parameter(ValueFromRemainingArguments=$true)][string[]]$Cmds)
Add-Type @"
using System; using System.Runtime.InteropServices;
public class FmWin { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }
"@
Add-Type -AssemblyName System.Windows.Forms
$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'javaw.exe' -and $_.CommandLine -match "instances[\\/]$([regex]::Escape($Inst))[\\/]" } | Select-Object -First 1
if (-not $p) { Write-Output "$Inst not running"; exit 1 }
$mc = "$env:APPDATA\PrismLauncher\instances\$Inst\minecraft"
$log = "$mc\logs\latest.log"
$before = (Get-Content $log).Count
[FmWin]::SetForegroundWindow((Get-Process -Id $p.ProcessId).MainWindowHandle) | Out-Null
Start-Sleep 1
foreach ($c in $Cmds) {
    if ($c -eq '@shot') {
        [System.Windows.Forms.SendKeys]::SendWait("{F2}"); Start-Sleep -Seconds 2
        $s = Get-ChildItem "$mc\screenshots" -Filter *.png -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
        Write-Output "shot: $($s.FullName)"; continue
    }
    if ($c -match '^@wait (\d+)$') { Start-Sleep -Seconds ([int]$Matches[1]); continue }
    if ($c -match '^@key (.+)$') { [System.Windows.Forms.SendKeys]::SendWait($Matches[1]); Start-Sleep -Milliseconds 700; continue }
    $esc = $c -replace '([+^%~(){}\[\]])', '{$1}'
    [System.Windows.Forms.SendKeys]::SendWait("t"); Start-Sleep -Milliseconds 900
    [System.Windows.Forms.SendKeys]::SendWait("$esc{ENTER}"); Start-Sleep -Seconds 2
}
Start-Sleep 2
Get-Content $log | Select-Object -Skip $before | Where-Object { $_ -match '\[CHAT\]|ERROR|Exception|FishDiag|failed|Unknown or incomplete|at fishmod|Caused by' } | ForEach-Object { $_.Substring(0, [Math]::Min(300, $_.Length)) }
Write-Output "done; alive=$([bool](Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue))"
