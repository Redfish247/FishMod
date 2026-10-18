# Sends chat commands to the running FishTest-26.2 window (singleplayer only) and prints new error lines from its log.
# Usage: powershell -NoProfile -File tools/fmtest.ps1 "/fm debug skyblock on" "/fm debug location set DWARVEN_MINES"
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Cmds)
Add-Type @"
using System; using System.Runtime.InteropServices;
public class FmWin { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }
"@
Add-Type -AssemblyName System.Windows.Forms
$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'javaw.exe' -and $_.CommandLine -like '*FishTest-26.2*' } | Select-Object -First 1
if (-not $p) { Write-Output "FishTest-26.2 not running"; exit 1 }
$log = "$env:APPDATA\PrismLauncher\instances\FishTest-26.2\minecraft\logs\latest.log"
$before = (Get-Content $log).Count
[FmWin]::SetForegroundWindow((Get-Process -Id $p.ProcessId).MainWindowHandle) | Out-Null
Start-Sleep 1
foreach ($c in $Cmds) {
    $esc = $c -replace '([+^%~(){}\[\]])', '{$1}'
    [System.Windows.Forms.SendKeys]::SendWait("t"); Start-Sleep -Milliseconds 900
    [System.Windows.Forms.SendKeys]::SendWait("$esc{ENTER}"); Start-Sleep -Seconds 2
}
Start-Sleep 2
Get-Content $log | Select-Object -Skip $before | Where-Object { $_ -match 'ERROR|Exception|FishDiag|failed|Unknown or incomplete|at fishmod|Caused by' } | ForEach-Object { $_.Substring(0, [Math]::Min(300, $_.Length)) }
Write-Output "done; alive=$([bool](Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue))"
