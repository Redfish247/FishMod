# Batch clicks/keys into a FishTest window at screenshot-pixel coords; pair with fmtest.ps1 for scripted UI test runs.
# Usage: click.ps1 -Inst FishTest-26.2 "c 1400 210" "r 900 300" "type hello" "key {ESC}" "scroll 900 500 -3" "wait 1"
param([string]$Inst = 'FishTest-26.2', [Parameter(ValueFromRemainingArguments=$true)][string[]]$Steps)
Add-Type @"
using System; using System.Runtime.InteropServices;
public class W {
 [StructLayout(LayoutKind.Sequential)] public struct P { public int X; public int Y; }
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref P p);
 [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
 [DllImport("user32.dll")] public static extern void mouse_event(uint f, int x, int y, int d, IntPtr e);
}
"@
Add-Type -AssemblyName System.Windows.Forms
$p = Get-CimInstance Win32_Process | ? { $_.Name -eq 'javaw.exe' -and $_.CommandLine -match "instances[\\/]$([regex]::Escape($Inst))[\\/]" } | select -First 1
if (-not $p) { "not running"; exit 1 }
$h = (Get-Process -Id $p.ProcessId).MainWindowHandle
[W]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 500
function Move-FmCursor($x, $y) { $pt = New-Object W+P; $pt.X = [int]$x; $pt.Y = [int]$y; [W]::ClientToScreen($h, [ref]$pt) | Out-Null; [W]::SetCursorPos($pt.X, $pt.Y) | Out-Null; Start-Sleep -Milliseconds 150 }
foreach ($s in $Steps) {
  $a = $s -split ' ', 2
  switch ($a[0]) {
    'c' { $xy = $a[1] -split ' '; Move-FmCursor $xy[0] $xy[1]; [W]::mouse_event(2,0,0,0,[IntPtr]::Zero); [W]::mouse_event(4,0,0,0,[IntPtr]::Zero) }
    'r' { $xy = $a[1] -split ' '; Move-FmCursor $xy[0] $xy[1]; [W]::mouse_event(8,0,0,0,[IntPtr]::Zero); [W]::mouse_event(16,0,0,0,[IntPtr]::Zero) }
    'm' { $xy = $a[1] -split ' '; Move-FmCursor $xy[0] $xy[1] }
    'scroll' { $xy = $a[1] -split ' '; Move-FmCursor $xy[0] $xy[1]; [W]::mouse_event(0x800,0,0,[int]$xy[2]*120,[IntPtr]::Zero) }
    'type' { [System.Windows.Forms.SendKeys]::SendWait(($a[1] -replace '([+^%~(){}\[\]])', '{$1}')) }
    'key' { [System.Windows.Forms.SendKeys]::SendWait($a[1]) }
    'wait' { Start-Sleep -Seconds ([double]$a[1]) }
  }
  Start-Sleep -Milliseconds 400
}
"ok"
