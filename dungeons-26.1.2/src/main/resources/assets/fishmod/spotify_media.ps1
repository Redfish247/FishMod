# FishMod Spotify HUD: prints one line per second. "M<tab>artist<tab>title<tab>posMs<tab>durMs<tab>playing<tab>artVersion" or "T<tab>window title" as fallback
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
function WinTitle { (Get-Process Spotify -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1).MainWindowTitle }
$mgr = $null
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    Add-Type -AssemblyName System.Drawing
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
    function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $null = $t.Wait(-1); $t.Result }
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch { $mgr = $null }
$img = Join-Path $env:TEMP 'fishmod_spotify_art.png'
$last = $null; $got = $false; $tries = 0; $ver = 0
while ($true) {
    $o = ''
    try {
        $s = $null
        if ($mgr) { $s = $mgr.GetSessions() | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' } | Select-Object -First 1 }
        if ($s) {
            $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $tl = $s.GetTimelineProperties()
            $playing = $s.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing'
            $key = $p.Artist + "`n" + $p.Title
            if ($key -ne $last) { $last = $key; $got = $false; $tries = 0; $ver++; Remove-Item $img -ErrorAction SilentlyContinue }
            if (-not $got -and $tries -lt 5 -and $p.Thumbnail) {
                $tries++
                try {
                    $st = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                    $n = [uint32]$st.Size
                    $r = [Windows.Storage.Streams.DataReader]::new($st)
                    $null = Await ($r.LoadAsync($n)) ([uint32])
                    $b = New-Object byte[] $n
                    $r.ReadBytes($b); $r.Dispose(); $st.Dispose()
                    $ms = New-Object System.IO.MemoryStream(, $b)
                    $im = [System.Drawing.Image]::FromStream($ms)
                    $im.Save($img, [System.Drawing.Imaging.ImageFormat]::Png)
                    $im.Dispose(); $ms.Dispose()
                    $got = $true; $ver++
                } catch {}
            }
            $pos = $tl.Position.TotalMilliseconds
            if ($playing) { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds }
            $dur = ($tl.EndTime - $tl.StartTime).TotalMilliseconds
            $o = "M`t" + ($p.Artist -replace '\s', ' ') + "`t" + ($p.Title -replace '\s', ' ') + "`t" + [long]$pos + "`t" + [long]$dur + "`t" + [int]$playing + "`t" + $ver
        } else { $o = "T`t" + (WinTitle) }
    } catch { $o = "T`t" + (WinTitle) }
    [Console]::Out.WriteLine($o); [Console]::Out.Flush()
    Start-Sleep -Milliseconds 1000
}
