param([string]$Time = '03:00')
# Registers a daily Task Scheduler job. Re-run with a new -Time to change it. Remove: Unregister-ScheduledTask FishModDailyBugfix
$script = 'D:\fishmoddungeons\tools\daily-bugfix\run-daily-bugfix.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$script`""
$trigger = New-ScheduledTaskTrigger -Daily -At $Time
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 6) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName 'FishModDailyBugfix' -Action $action -Trigger $trigger -Settings $settings -Force | Out-Null
"Registered FishModDailyBugfix daily at $Time"
