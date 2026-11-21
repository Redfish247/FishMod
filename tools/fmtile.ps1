# Tiles running FishTest windows side by side across the top half of the main screen so Eli can watch test runs.
Add-Type @"
using System; using System.Runtime.InteropServices;
public class T { [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int ht, bool r);
 [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c); }
"@
Add-Type -AssemblyName System.Windows.Forms
$wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$wins = foreach ($n in 'FishTest', 'FishTest-26.2', 'FishTest-26.3') {
    $p = Get-CimInstance Win32_Process | ? { $_.Name -eq 'javaw.exe' -and $_.CommandLine -match "instances[\\/]$([regex]::Escape($n))[\\/]" } | select -First 1
    if ($p) { $h = (Get-Process -Id $p.ProcessId).MainWindowHandle; if ($h -ne [IntPtr]::Zero) { [pscustomobject]@{ n = $n; h = $h } } }
}
$k = @($wins).Count; if ($k -eq 0) { "no FishTest windows"; exit 1 }
$w = [int]($wa.Width / $k); $ht = [int]($wa.Height / 2)
$i = 0
foreach ($x in $wins) { [T]::ShowWindow($x.h, 9) | Out-Null; [T]::MoveWindow($x.h, $wa.X + $i * $w, $wa.Y, $w, $ht, $true) | Out-Null; "$($x.n) -> slot $i ($w x $ht)"; $i++ }
