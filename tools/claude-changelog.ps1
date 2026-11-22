# Posts a report JSON to the hidden fishmod.dev Claude Changelog page. -Replace overwrites the day; default merges.
param([Parameter(Mandatory)][string]$File, [switch]$Replace)
$ErrorActionPreference = 'Stop'
$secret = $env:ROADMAP_SECRET
if (-not $secret) { $secret = [Environment]::GetEnvironmentVariable('ROADMAP_SECRET', 'User') }
if (-not $secret) { Write-Error 'ROADMAP_SECRET is not set'; exit 1 }
$report = Get-Content -Raw -Encoding UTF8 $File | ConvertFrom-Json
if (-not $report.date) { $report | Add-Member date (Get-Date -Format 'yyyy-MM-dd') }
$report | Add-Member -Force merge (-not $Replace)
$body = [Text.Encoding]::UTF8.GetBytes(($report | ConvertTo-Json -Depth 10))
$r = Invoke-RestMethod -Method Post -Uri 'https://fishmod.dev/dashboard/api/claude-changelog' `
  -Headers @{ Authorization = "Bearer $secret" } -ContentType 'application/json; charset=utf-8' -Body $body
$r | ConvertTo-Json -Compress
