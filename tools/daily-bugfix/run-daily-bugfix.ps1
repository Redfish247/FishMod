# Headless daily bugfix run. Scheduled by register-daily-bugfix.ps1.
$repo = 'D:\fishmoddungeons'
$logDir = Join-Path $repo 'tools\daily-bugfix\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir ("run-{0:yyyy-MM-dd_HH-mm}.log" -f (Get-Date))

Set-Location $repo
$dirty = git status --porcelain
if ($dirty) { "Skipped: working tree has uncommitted changes (Eli's WIP). Commit them and it runs tomorrow." | Out-File $log; exit 0 }
git pull --ff-only 2>&1 | Out-File $log -Append

$prompt = Get-Content (Join-Path $repo 'tools\daily-bugfix\prompt.md') -Raw
# acceptEdits + explicit tool allowlist so the unattended run never blocks on a prompt
claude -p $prompt --permission-mode acceptEdits `
  --allowedTools "Bash" "PowerShell" "Read" "Edit" "Write" "Glob" "Grep" "WebFetch" `
  2>&1 | Out-File $log -Append -Encoding utf8
