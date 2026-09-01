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
		$props = Await-Op ($best.TryGetMediaPropertiesAsync()) 1200
		if ($null -ne $props) {
			$title = [string]$props.Title
			$artist = [string]$props.Artist
			$album = [string]$props.AlbumTitle
			if ([string]::IsNullOrWhiteSpace($artist)) {
				$artist = [string]$props.AlbumArtist
			}
		}
		if ([string]::IsNullOrWhiteSpace($title)) {
			Emit-Idle ('empty-title:' + $app)
			Start-Sleep -Milliseconds 500
			continue
		}
		$playing = ($status -eq 'Playing')
		$line = '{"ok":true,"app":"' + (Json-Escape $app) + '","title":"' + (Json-Escape $title) + '","artist":"' + (Json-Escape $artist) + '","album":"' + (Json-Escape $album) + '","playing":' + ($(if ($playing) { 'true' } else { 'false' })) + ',"position":' + $pos + ',"duration":' + $dur + '}'
		Emit $line
	} catch {
		Emit-Idle 'poll-error'
	}
	Start-Sleep -Milliseconds 400
}
