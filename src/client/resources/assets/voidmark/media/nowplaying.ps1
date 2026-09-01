param(
	[string]$OutFile = ''
)

$ErrorActionPreference = 'SilentlyContinue'
$utf8 = New-Object System.Text.UTF8Encoding $false

function Emit([string]$line) {
	if (-not [string]::IsNullOrWhiteSpace($OutFile)) {
		try {
			[System.IO.File]::WriteAllText($OutFile, $line, $utf8)
		} catch {}
	}
	try {
		$stdout = [Console]::OpenStandardOutput()
		$writer = New-Object System.IO.StreamWriter($stdout, $utf8)
		$writer.AutoFlush = $true
		$writer.WriteLine($line)
		$writer.Flush()
	} catch {
		[Console]::Out.WriteLine($line)
		[Console]::Out.Flush()
	}
}

function Emit-Idle([string]$reason) {
	$safe = if ($null -eq $reason) { '' } else { $reason.Replace('\', '\\').Replace('"', '\"') }
	Emit ('{"ok":false,"err":"' + $safe + '"}')
}

function Json-Escape([string]$value) {
	if ($null -eq $value) {
		return ''
	}
	return ($value.Replace('\', '\\').Replace('"', '\"').Replace("`r", ' ').Replace("`n", ' ').Replace("`t", ' '))
}

function Cover-FromStream($ras) {
	try { $ras.Seek(0) } catch {}
	try {
		[Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime] | Out-Null
		$reader = [Windows.Storage.Streams.DataReader]::new($ras)
		$size = 0
		try { $size = [int64]$ras.Size } catch { $size = 0 }
		$want = $size
		if ($want -lt 32 -or $want -gt 2000000) { $want = 1048576 }
		$null = Await-Op ($reader.LoadAsync([uint32]$want)) 3000
		$avail = 0
		try { $avail = [int]$reader.UnconsumedBufferLength } catch { $avail = 0 }
		if ($avail -ge 32) {
			$bytes = New-Object byte[] $avail
			for ($i = 0; $i -lt $avail; $i++) {
				$bytes[$i] = $reader.ReadByte()
			}
			try { $reader.DetachStream() | Out-Null } catch {}
			try { $reader.Dispose() } catch {}
			return $bytes
		}
		try { $reader.DetachStream() | Out-Null } catch {}
		try { $reader.Dispose() } catch {}
	} catch {}
	try { $ras.Seek(0) } catch {}
	try {
		Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction Stop
		$net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($ras)
		$ms = New-Object System.IO.MemoryStream
		$net.CopyTo($ms)
		$bytes = $ms.ToArray()
		try { $ms.Dispose() } catch {}
		if ($bytes.Length -ge 32) {
			return $bytes
		}
	} catch {}
	return $null
}

function Save-Cover($props, [string]$path) {
	try {
		if ($null -eq $props -or [string]::IsNullOrWhiteSpace($path)) {
			return $false
		}
		$thumb = $null
		try { $thumb = $props.Thumbnail } catch { return $false }
		if ($null -eq $thumb) {
			return $false
		}
		$ras = Await-Op ($thumb.OpenReadAsync()) 2000
		if ($null -eq $ras) {
			return $false
		}
		$bytes = $null
		try {
			$bytes = Cover-FromStream $ras
		} finally {
			try { $ras.Dispose() } catch {}
		}
		if ($null -eq $bytes -or $bytes.Length -lt 32) {
			return $false
		}
		$dir = [System.IO.Path]::GetDirectoryName($path)
		if (-not [string]::IsNullOrWhiteSpace($dir)) {
			[System.IO.Directory]::CreateDirectory($dir) | Out-Null
		}
		$tmp = $path + '.tmp'
		[System.IO.File]::WriteAllBytes($tmp, $bytes)
		[System.IO.File]::Copy($tmp, $path, $true)
		try { [System.IO.File]::Delete($tmp) } catch {}
		return $true
	} catch {
		return $false
	}
}

function Await-Op($op, $timeoutMs) {
	if ($null -eq $op) {
		return $null
	}
	$start = [Environment]::TickCount
	while ($true) {
		$st = [string]$op.Status
		if ($st -eq 'Completed' -or $st -eq '1') {
			try { return $op.GetResults() } catch { return $null }
		}
		if ($st -eq 'Error' -or $st -eq 'Canceled' -or $st -eq '3' -or $st -eq '2') {
			return $null
		}
		if (([Environment]::TickCount - $start) -gt $timeoutMs) {
			return $null
		}
		Start-Sleep -Milliseconds 20
	}
}

function Score-App([string]$id) {
	$lower = ([string]$id).ToLowerInvariant()
	if ($lower -match 'spotify') { return 120 }
	if ($lower -match 'youtubemusic|youtube\.music|youtube-music|ytm|cider') { return 110 }
	if ($lower -match 'youtube') { return 90 }
	if ($lower -match 'electron|music') { return 70 }
	if ($lower -match 'chrome|msedge|brave|firefox|opera|vivaldi') { return 40 }
	if ($lower -match 'vlc|wmplayer|groove|zune|itunes|apple|foobar|mpv') { return 20 }
	return 5
}

try {
	[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null
} catch {
	Emit-Idle 'winrt-missing'
	while ($true) { Start-Sleep -Seconds 5 }
}

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) 4000
if ($null -eq $mgr) {
	Emit-Idle 'session-manager'
	while ($true) { Start-Sleep -Seconds 2 }
}

$artPath = ''
if (-not [string]::IsNullOrWhiteSpace($OutFile)) {
	$artPath = [System.IO.Path]::Combine([System.IO.Path]::GetDirectoryName($OutFile), 'voidmark-nowplaying-art.bin')
}
$script:lastArtKey = ''
$script:artOk = $false
$script:nextArtTry = 0

while ($true) {
	try {
		$best = $null
		$bestScore = -1
		$sessions = @()
		try { $sessions = @($mgr.GetSessions()) } catch { $sessions = @() }
		if ($sessions.Count -eq 0) {
			try {
				$cur = $mgr.GetCurrentSession()
				if ($null -ne $cur) { $sessions = @($cur) }
			} catch {}
		}
		foreach ($session in $sessions) {
			if ($null -eq $session) { continue }
			$score = Score-App ([string]$session.SourceAppUserModelId)
			try {
				$playback = $session.GetPlaybackInfo()
				$status = if ($null -ne $playback) { [string]$playback.PlaybackStatus } else { '' }
				if ($status -eq 'Playing') { $score += 25 }
				elseif ($status -eq 'Paused' -or $status -eq 'Opened') { $score += 8 }
			} catch {}
			if ($score -gt $bestScore) {
				$bestScore = $score
				$best = $session
			}
		}
		if ($null -eq $best) {
			Emit-Idle 'no-session'
			$script:lastArtKey = ''
			$script:artOk = $false
			Start-Sleep -Milliseconds 500
			continue
		}

		$app = [string]$best.SourceAppUserModelId
		$status = ''
		try {
			$playback = $best.GetPlaybackInfo()
			if ($null -ne $playback) { $status = [string]$playback.PlaybackStatus }
		} catch {}
		$pos = 0L
		$dur = 0L
		try {
			$timeline = $best.GetTimelineProperties()
			if ($null -ne $timeline) {
				$pos = [int64]$timeline.Position.Ticks
				$end = [int64]$timeline.EndTime.Ticks
				$max = [int64]$timeline.MaxSeekTime.Ticks
				$dur = [Math]::Max($end, $max)
			}
		} catch {}
		$title = ''
		$artist = ''
		$album = ''
		$subtitle = ''
		$albumArtist = ''
		$props = Await-Op ($best.TryGetMediaPropertiesAsync()) 1200
		if ($null -ne $props) {
			$title = [string]$props.Title
			$artist = [string]$props.Artist
			$album = [string]$props.AlbumTitle
			try { $subtitle = [string]$props.Subtitle } catch { $subtitle = '' }
			try { $albumArtist = [string]$props.AlbumArtist } catch { $albumArtist = '' }
			if ([string]::IsNullOrWhiteSpace($artist)) {
				$artist = $albumArtist
			}
		}
		if ([string]::IsNullOrWhiteSpace($title)) {
			Emit-Idle ('empty-title:' + $app)
			$script:lastArtKey = ''
			$script:artOk = $false
			Start-Sleep -Milliseconds 500
			continue
		}
		$playing = ($status -eq 'Playing')
		$artField = ''
		if (-not [string]::IsNullOrWhiteSpace($artPath) -and $null -ne $props) {
			$artKey = $title + '|' + $artist + '|' + $album
			$nowTick = [Environment]::TickCount
			if ($artKey -ne $script:lastArtKey) {
				$script:lastArtKey = $artKey
				$script:artOk = Save-Cover $props $artPath
				$script:nextArtTry = $nowTick + 2000
			} elseif (-not $script:artOk -and ($nowTick - $script:nextArtTry) -gt 0) {
				$script:artOk = Save-Cover $props $artPath
				$script:nextArtTry = $nowTick + 2000
			}
			if ($script:artOk -and (Test-Path -LiteralPath $artPath)) {
				$artField = ',"art":"' + (Json-Escape ($artPath.Replace('\', '/'))) + '"'
			}
		}
		$line = '{"ok":true,"app":"' + (Json-Escape $app) + '","title":"' + (Json-Escape $title) + '","artist":"' + (Json-Escape $artist) + '","subtitle":"' + (Json-Escape $subtitle) + '","albumArtist":"' + (Json-Escape $albumArtist) + '","album":"' + (Json-Escape $album) + '","playing":' + ($(if ($playing) { 'true' } else { 'false' })) + ',"position":' + $pos + ',"duration":' + $dur + $artField + '}'
		Emit $line
	} catch {
		Emit-Idle 'poll-error'
	}
	Start-Sleep -Milliseconds 400
}
