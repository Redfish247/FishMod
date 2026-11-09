code -n "D:\fishmoddungeons\CHANGELOG.txt"
Start-Sleep -Seconds 4
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System; using System.Runtime.InteropServices;
public class W {
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h,int x,int y,int w,int ht,bool r);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h,int c);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
"@
$s = [System.Windows.Forms.Screen]::AllScreens | Where-Object { $_.DeviceName -eq '\\.\DISPLAY1' }
if (-not $s) { $s = [System.Windows.Forms.Screen]::PrimaryScreen }
$p = Get-Process Code -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*CHANGELOG.txt*' } | Select-Object -First 1
if (-not $p) { Write-Output "Couldn't find the VS Code window"; exit 1 }
$h = $p.MainWindowHandle
$b = $s.WorkingArea
[W]::ShowWindow($h, 9) | Out-Null
[W]::MoveWindow($h, $b.X, $b.Y, $b.Width, $b.Height, $true) | Out-Null
[W]::ShowWindow($h, 3) | Out-Null
[W]::SetForegroundWindow($h) | Out-Null
