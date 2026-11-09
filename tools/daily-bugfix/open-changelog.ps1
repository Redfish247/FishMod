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
Add-Type @"
using System; using System.Runtime.InteropServices;
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Ansi)] public struct DD { public int cb; [MarshalAs(UnmanagedType.ByValTStr, SizeConst=32)] public string DeviceName; [MarshalAs(UnmanagedType.ByValTStr, SizeConst=128)] public string DeviceString; public int StateFlags; [MarshalAs(UnmanagedType.ByValTStr, SizeConst=128)] public string DeviceID; [MarshalAs(UnmanagedType.ByValTStr, SizeConst=128)] public string DeviceKey; }
public class E { [DllImport("user32.dll", CharSet=CharSet.Ansi)] public static extern bool EnumDisplayDevices(string d, uint i, ref DD dd, uint f); }
"@
# VG279QM5A = monitor hardware id AUS2759 (GDI \\.\DISPLAYn numbers differ from Windows Settings numbers)
$s = [System.Windows.Forms.Screen]::AllScreens | Where-Object {
  $d = New-Object DD; $d.cb = [Runtime.InteropServices.Marshal]::SizeOf($d)
  [E]::EnumDisplayDevices($_.DeviceName, 0, [ref]$d, 0) -and $d.DeviceID -like '*AUS2759*'
} | Select-Object -First 1
if (-not $s) { $s = [System.Windows.Forms.Screen]::PrimaryScreen }
$p = Get-Process Code -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*CHANGELOG.txt*' } | Select-Object -First 1
if (-not $p) { Write-Output "Couldn't find the VS Code window"; exit 1 }
$h = $p.MainWindowHandle
$b = $s.WorkingArea
[W]::ShowWindow($h, 9) | Out-Null
[W]::MoveWindow($h, $b.X, $b.Y, $b.Width, $b.Height, $true) | Out-Null
[W]::ShowWindow($h, 3) | Out-Null
[W]::SetForegroundWindow($h) | Out-Null
