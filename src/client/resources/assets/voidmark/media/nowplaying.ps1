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

function Kind-App([string]$id) {
	$lower = ([string]$id).ToLowerInvariant()
	if ($lower -match 'spotify') { return 'spotify' }
	if ($lower -match 'youtubemusic|youtube\.music|youtube-music|ytm|cider') { return 'ytm' }
	if ($lower -match 'edge|chrome|brave|firefox|opera|vivaldi') { return 'browser' }
	if ($lower -match 'electron') { return 'ytm' }
	return 'windows'
}

function Playback-Code($status) {
	if ($null -eq $status) {
		return -1
	}
	$name = ([string]$status).Trim()
	if ([string]::IsNullOrWhiteSpace($name)) {
		return -1
	}
	switch ($name) {
		'Closed' { return 0 }
		'Opened' { return 1 }
		'Changing' { return 2 }
		'Stopped' { return 3 }
		'Playing' { return 4 }
		'Paused' { return 5 }
		default {
			if ($name -match '^\d+$') {
				return [int]$name
			}
			try { return [int]$status.Value__ } catch {}
			return -1
		}
	}
}

# SMTC PlaybackStatus: Closed=0 Opened=1 Changing=2 Stopped=3 Playing=4 Paused=5
# PowerShell often stringifies these as "4" / "5" instead of Playing / Paused.
function Is-Playing($playback) {
	if ($null -eq $playback) {
		return $true
	}
	$raw = $null
	try { $raw = $playback.PlaybackStatus } catch { return $true }
	$code = Playback-Code $raw
	if ($code -eq 4) { return $true }
	if ($code -ge 0) { return $false }
	$name = ([string]$raw).Trim()
	if ($name -eq 'Playing') { return $true }
	if ($name -eq 'Paused' -or $name -eq 'Stopped' -or $name -eq 'Closed' -or $name -eq 'Opened' -or $name -eq 'Changing') {
		return $false
	}
	return $true
}

function Score-App([string]$id) {
	$lower = ([string]$id).ToLowerInvariant()
	if ($lower -match 'spotify') { return 120 }
	if ($lower -match 'youtubemusic|youtube\.music|youtube-music|ytm|cider') { return 110 }
	if ($lower -match 'youtube') { return 90 }
	if ($lower -match 'electron|music') { return 70 }
	if ($lower -match 'edge|chrome|brave|firefox|opera|vivaldi') { return 40 }
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
				$code = if ($null -ne $playback) { Playback-Code $playback.PlaybackStatus } else { -1 }
				if ($code -eq 4) { $score += 25 }
				elseif ($code -eq 5 -or $code -eq 1 -or $code -eq 3) { $score += 8 }
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
		$playing = $true
		try {
			$playback = $best.GetPlaybackInfo()
			$playing = Is-Playing $playback
		} catch {}
		$posMs = 0L
		$durMs = 0L
		try {
			$timeline = $best.GetTimelineProperties()
			if ($null -ne $timeline) {
				try { $posMs = [int64][Math]::Max(0, $timeline.Position.TotalMilliseconds) } catch { $posMs = 0L }
				$endMs = 0L
				$maxMs = 0L
				try { $endMs = [int64][Math]::Max(0, $timeline.EndTime.TotalMilliseconds) } catch {}
				try { $maxMs = [int64][Math]::Max(0, $timeline.MaxSeekTime.TotalMilliseconds) } catch {}
				$durMs = [Math]::Max($endMs, $maxMs)
				if ($posMs -le 0) {
					try { $posMs = [int64]([Math]::Max(0, $timeline.Position.Ticks) / 10000) } catch {}
				}
				if ($durMs -le 0) {
					try {
						$end = [int64]$timeline.EndTime.Ticks
						$max = [int64]$timeline.MaxSeekTime.Ticks
						$durMs = [int64]([Math]::Max($end, $max) / 10000)
					} catch {}
				}
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
		}
		if ([string]::IsNullOrWhiteSpace($title)) {
			Emit-Idle ('empty-title:' + $app)
			$script:lastArtKey = ''
			$script:artOk = $false
			Start-Sleep -Milliseconds 500
			continue
		}
		$kind = Kind-App $app
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
		$line = '{"ok":true,"app":"' + (Json-Escape $app) + '","kind":"' + (Json-Escape $kind) + '","title":"' + (Json-Escape $title) + '","artist":"' + (Json-Escape $artist) + '","subtitle":"' + (Json-Escape $subtitle) + '","albumArtist":"' + (Json-Escape $albumArtist) + '","album":"' + (Json-Escape $album) + '","playing":' + ($(if ($playing) { 'true' } else { 'false' })) + ',"position":' + $posMs + ',"duration":' + $durMs + ',"positionMs":' + $posMs + ',"durationMs":' + $durMs + $artField + '}'
		Emit $line
	} catch {
		Emit-Idle 'poll-error'
	}
	Start-Sleep -Milliseconds 400
}
