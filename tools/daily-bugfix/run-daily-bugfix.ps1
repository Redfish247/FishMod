# Headless daily bugfix run. Scheduled by register-daily-bugfix.ps1.
$repo = 'D:\fishmoddungeons'
$logDir = Join-Path $repo 'tools\daily-bugfix\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir ("run-{0:yyyy-MM-dd_HH-mm}.log" -f (Get-Date))
function Log($m) { $m | Out-File $log -Append -Encoding utf8 }
$ErrorActionPreference = 'Continue'

Set-Location $repo
# Never skip on a dirty tree (that silently killed 11-23): save WIP as its own commit on main so the run starts clean.
$dirty = (cmd /c "git status --porcelain 2>&1")
if ($dirty) {
  Log "Dirty tree, committing WIP first:`n$dirty"
  cmd /c "git add -A 2>&1" | ForEach-Object { Log $_ }
  cmd /c "git commit -q -m `"wip: autosave before daily bugfix run (not built)`" 2>&1" | ForEach-Object { Log $_ }
}
# Rebase instead of --ff-only (diverged main killed 11-22). If it fails, abort and carry on with local main.
cmd /c "git fetch origin 2>&1" | ForEach-Object { Log $_ }
$pull = cmd /c "git pull --rebase --autostash origin main 2>&1"
$pull | ForEach-Object { Log $_ }
if ($LASTEXITCODE -ne 0) {
  Log "Pull/rebase failed, aborting rebase and continuing on local main."
  cmd /c "git rebase --abort 2>&1" | ForEach-Object { Log $_ }
}

$prompt = Get-Content (Join-Path $repo 'tools\daily-bugfix\prompt.md') -Raw
# acceptEdits + explicit tool allowlist so the unattended run never blocks on a prompt
$out = $prompt | claude -p --permission-mode acceptEdits `
  --allowedTools "Bash" "PowerShell" "Read" "Edit" "Write" "Glob" "Grep" "WebFetch" 2>&1
$out | ForEach-Object { Log "$_" }
if (($out -join "`n") -match "hit your .*limit|out of usage credits") {
  Log "USAGE LIMIT HIT: run did not complete. Re-run after reset: Start-ScheduledTask FishModDailyBugfix"
}
