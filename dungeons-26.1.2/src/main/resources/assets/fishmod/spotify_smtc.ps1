# Polls the Windows media session for Spotify every 2 s; one base64 line per poll, thumbnail written to $args[0] on track change
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Aw($op, $t) { $k = $asTask.MakeGenericMethod($t).Invoke($null, @($op)); $k.Wait(-1) | Out-Null; $k.Result }
function B($s) { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$s)) }
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
$mgr = Aw ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$art = $args[0]; $lastKey = ''; $gen = 0
while ($true) {
    $line = '-'
    try {
        foreach ($s in $mgr.GetSessions()) {
            if ($s.SourceAppUserModelId -notlike '*Spotify*') { continue }
            $p = Aw ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $tl = $s.GetTimelineProperties(); $pb = $s.GetPlaybackInfo()
            $key = $p.Title + '|' + $p.Artist + '|' + $p.AlbumTitle
            if ($key -ne $lastKey) {
                $lastKey = $key
                try {
                    $st = Aw ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                    $r = [Windows.Storage.Streams.DataReader]::new($st)
                    [void](Aw ($r.LoadAsync([uint32]$st.Size)) ([uint32]))
                    $buf = New-Object byte[] $st.Size; $r.ReadBytes($buf)
                    [IO.File]::WriteAllBytes($art, $buf); $gen++
                } catch {}
            }
            $play = if ($pb.PlaybackStatus -eq 4) { 1 } else { 0 }
            $pos = [int64]($tl.Position.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
            $dur = [int64]($tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
            $line = (B $p.Title) + ' ' + (B $p.Artist) + ' ' + (B $p.AlbumTitle) + ' ' + $pos + ' ' + $dur + ' ' + $play + ' ' + $gen
            break
        }
    } catch {}
    [Console]::Out.WriteLine($line); [Console]::Out.Flush()
    Start-Sleep -Seconds 2
}
