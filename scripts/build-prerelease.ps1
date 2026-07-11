<#
Builds FishModDungeons for both the 1.21.11 and 26.1.2 modules and posts a prerelease
notification to the Discord prerelease-chat webhook.

Usage: powershell scripts/build-prerelease.ps1 [-Notes "what's new"]

Webhook source (first found wins): -WebhookUrl, then env DISCORD_WEBHOOK, then gradle
property discord_webhook in ~/.gradle/gradle.properties (never this repo's tracked
gradle.properties).
#>
param(
    [string]$Notes = "",
    [string]$WebhookUrl = "",
    [string]$PingRoleId = ""
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot

if (-not $WebhookUrl) {
    $WebhookUrl = $env:DISCORD_WEBHOOK
}
if (-not $WebhookUrl) {
    $gradleProps = Join-Path $HOME ".gradle/gradle.properties"
    if (Test-Path $gradleProps) {
        $line = Get-Content $gradleProps | Where-Object { $_ -match '^\s*discord_webhook\s*=' } | Select-Object -First 1
        if ($line) { $WebhookUrl = ($line -split '=', 2)[1].Trim() }
    }
}
if (-not $WebhookUrl) {
    throw "No Discord webhook found. Pass -WebhookUrl, set DISCORD_WEBHOOK, or add discord_webhook to ~/.gradle/gradle.properties."
}

$Modules = @(
    @{ Name = "1.21.11"; Gradle = ":dungeons-1.21.11" },
    @{ Name = "26.1.2";  Gradle = ":dungeons-26.1.2" }
)

$builtJars = @()

Push-Location $RepoRoot
try {
    foreach ($mod in $Modules) {
        Write-Host "Building $($mod.Name)..."
        & "$RepoRoot/gradlew.bat" "$($mod.Gradle):build"
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for $($mod.Name)"
        }

        $modDir = Join-Path $RepoRoot ($mod.Gradle.TrimStart(':'))
        $props = Get-Content (Join-Path $modDir "gradle.properties") -Raw
        $modVersion = ([regex]::Match($props, 'mod_version=(\S+)')).Groups[1].Value
        $baseName = ([regex]::Match($props, 'archives_base_name=(\S+)')).Groups[1].Value

        $jarPath = Join-Path $modDir "build/libs/$baseName-$modVersion.jar"
        if (-not (Test-Path $jarPath)) {
            throw "Expected jar not found: $jarPath"
        }

        $builtJars += [PSCustomObject]@{
            Track   = $mod.Name
            Version = $modVersion
            JarPath = $jarPath
            JarName = Split-Path $jarPath -Leaf
        }
    }
} finally {
    Pop-Location
}

$jarList = ($builtJars | ForEach-Object { "- **$($_.Track)**: ``$($_.JarName)``" }) -join "`n"
$description = "New FishModDungeons prerelease built for both tracks:`n$jarList"
if ($Notes) {
    $description += "`n`n$Notes"
}

$content = ""
$allowedMentions = @{ parse = @() }
if ($PingRoleId) {
    $content = "<@&$PingRoleId>"
    $allowedMentions = @{ roles = @($PingRoleId) }
}

$payload = @{
    content = $content
    allowed_mentions = $allowedMentions
    embeds = @(
        @{
            title       = "FishModDungeons Prerelease"
            description = $description
            color       = 3447003
        }
    )
} | ConvertTo-Json -Depth 5

# Discord webhooks need the jars attached as real multipart file parts (not just linked/named in
# the embed) for people in the channel to download them directly - Invoke-RestMethod on Windows
# PowerShell 5.1 has no built-in multi-file multipart support, so this builds the request by hand
# with System.Net.Http instead.
Add-Type -AssemblyName System.Net.Http

$httpClient = New-Object System.Net.Http.HttpClient
try {
    $multipart = New-Object System.Net.Http.MultipartFormDataContent
    try {
        $jsonContent = New-Object System.Net.Http.StringContent($payload, [System.Text.Encoding]::UTF8, "application/json")
        $multipart.Add($jsonContent, "payload_json")

        $fileStreams = @()
        for ($i = 0; $i -lt $builtJars.Count; $i++) {
            $jar = $builtJars[$i]
            $stream = [System.IO.File]::OpenRead($jar.JarPath)
            $fileStreams += $stream
            $fileContent = New-Object System.Net.Http.StreamContent($stream)
            $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/java-archive")
            $multipart.Add($fileContent, "files[$i]", $jar.JarName)
        }

        $response = $httpClient.PostAsync($WebhookUrl, $multipart).GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            throw "Discord webhook POST failed: $($response.StatusCode) - $body"
        }
    } finally {
        foreach ($stream in $fileStreams) { $stream.Dispose() }
        $multipart.Dispose()
    }
} finally {
    $httpClient.Dispose()
}

Write-Host "Posted prerelease notification to Discord (with jar attachments)."
$builtJars | Format-Table Track, Version, JarPath -AutoSize
