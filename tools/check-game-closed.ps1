# Usage: check-game-closed.ps1 <instance>   (Skyblock | FishTest)
# Exit 0 = no javaw for that Prism instance is running, exit 1 = running.
param([Parameter(Mandatory = $true)][string]$Instance)
$hits = Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*instances*$Instance*" }
if ($hits) { Write-Output "RUNNING: $Instance"; exit 1 }
Write-Output "closed: $Instance"
exit 0
