<#
Builds FishModDungeons for both 1.21.11 and 26.1.2 and posts a prerelease notification
to the Discord prerelease-chat webhook.

Usage: pwsh scripts/build-prerelease.ps1 [-Notes "what's new"]
#>
param(
    [string]$Notes = ""
)

$ErrorActionPreference = "Stop"

$WebhookUrl = "https://discord.com/api/webhooks/1516247063783018596/WOMRFNYh2oxq08amo7DIE-vOK5Yb752SptpaxJ_hDFLkYD9DJx186H8VdrnIlsbW9qMX"

$Tracks = @(
    @{ Name = "1.21.11"; Path = "D:/FishMod-dungeons-1.21.11-slim" },
    @{ Name = "26.1.2";  Path = "D:/FishMod-dungeons-26.1.2-slim" }
)

$builtJars = @()

foreach ($track in $Tracks) {
    $path = $track.Path
    if (-not (Test-Path $path)) {
        throw "Worktree not found for $($track.Name): $path"
    }

    Write-Host "Building $($track.Name) at $path..."
    Push-Location $path
    try {
        & "$path/gradlew.bat" build
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for $($track.Name)"
        }
    } finally {
        Pop-Location
    }

    $props = Get-Content "$path/gradle.properties" -Raw
    $modVersion = ([regex]::Match($props, 'mod_version=(\S+)')).Groups[1].Value
    $baseName = ([regex]::Match($props, 'archives_base_name=(\S+)')).Groups[1].Value

    $jarPath = Join-Path $path "build/libs/$baseName-$modVersion.jar"
    if (-not (Test-Path $jarPath)) {
        throw "Expected jar not found: $jarPath"
    }

    $builtJars += [PSCustomObject]@{
        Track   = $track.Name
        Version = $modVersion
        JarPath = $jarPath
        JarName = Split-Path $jarPath -Leaf
    }
}

$jarList = ($builtJars | ForEach-Object { "- **$($_.Track)**: `$($_.JarName)`" }) -join "`n"
$description = "New FishMod Dungeons prerelease built for both tracks:`n$jarList"
if ($Notes) {
    $description += "`n`n$Notes"
}

$payload = @{
    embeds = @(
        @{
            title       = "FishMod Dungeons Prerelease"
            description = $description
            color       = 3447003
        }
    )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Uri $WebhookUrl -Method Post -ContentType "application/json" -Body $payload

Write-Host "Posted prerelease notification to Discord."
$builtJars | Format-Table Track, Version, JarPath -AutoSize
