$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [Console]::OutputEncoding

function Wait-Op($op, $timeoutMs) {
	if ($null -eq $op) {
		return $null
	}
	$start = [Environment]::TickCount
	while ($op.Status -eq 0) {
		if (([Environment]::TickCount - $start) -gt $timeoutMs) {
			return $null
		}
		Start-Sleep -Milliseconds 15
	}
	if ($op.Status -ne 1) {
		return $null
	}
	return $op.GetResults()
}

function Write-Idle {
	[Console]::Out.WriteLine('{"ok":false}')
	[Console]::Out.Flush()
}

function Json-Escape([string]$value) {
	if ($null -eq $value) {
		return ''
	}
	return ($value.Replace('\', '\\').Replace('"', '\"').Replace("`r", ' ').Replace("`n", ' '))
}

function Score-App([string]$id) {
	$lower = $id.ToLowerInvariant()
	if ($lower -match 'spotify') { return 100 }
	if ($lower -match 'youtubemusic|youtube\.music|cider') { return 90 }
	if ($lower -match 'youtube') { return 80 }
	if ($lower -match 'chrome|msedge|brave|firefox|opera') { return 40 }
	if ($lower -match 'vlc|wmplayer|groove|zune|itunes|apple') { return 20 }
	return 1
}

try {
	[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
} catch {
	while ($true) {
		Write-Idle
		Start-Sleep -Milliseconds 800
	}
}

$mgr = Wait-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) 2500
if ($null -eq $mgr) {
	while ($true) {
		Write-Idle
		Start-Sleep -Milliseconds 800
	}
}

while ($true) {
	try {
		$best = $null
		$bestScore = -1
		foreach ($session in @($mgr.GetSessions())) {
			if ($null -eq $session) { continue }
			$score = Score-App ([string]$session.SourceAppUserModelId)
			$playback = $session.GetPlaybackInfo()
			$status = if ($null -ne $playback) { [string]$playback.PlaybackStatus } else { '' }
			if ($status -eq 'Playing') { $score += 8 }
			if ($score -gt $bestScore) {
				$bestScore = $score
				$best = $session
			}
		}
		if ($null -eq $best) {
			$best = $mgr.GetCurrentSession()
		}
		if ($null -eq $best) {
			Write-Idle
			Start-Sleep -Milliseconds 450
			continue
		}

		$app = [string]$best.SourceAppUserModelId
		$playback = $best.GetPlaybackInfo()
		$status = if ($null -ne $playback) { [string]$playback.PlaybackStatus } else { 'Closed' }
		$timeline = $best.GetTimelineProperties()
		$pos = 0L
		$dur = 0L
		if ($null -ne $timeline) {
			$pos = [int64]$timeline.Position.Ticks
			$dur = [int64]$timeline.EndTime.Ticks
		}
		$props = Wait-Op ($best.TryGetMediaPropertiesAsync()) 800
		$title = ''
		$artist = ''
		$album = ''
		if ($null -ne $props) {
			$title = [string]$props.Title
			$artist = [string]$props.Artist
			$album = [string]$props.AlbumTitle
		}
		if ([string]::IsNullOrWhiteSpace($title) -and [string]::IsNullOrWhiteSpace($artist)) {
			Write-Idle
			Start-Sleep -Milliseconds 450
			continue
		}
		$playing = ($status -eq 'Playing')
		$line = '{"ok":true,"app":"' + (Json-Escape $app) + '","title":"' + (Json-Escape $title) + '","artist":"' + (Json-Escape $artist) + '","album":"' + (Json-Escape $album) + '","playing":' + ($(if ($playing) { 'true' } else { 'false' })) + ',"position":' + $pos + ',"duration":' + $dur + '}'
		[Console]::Out.WriteLine($line)
		[Console]::Out.Flush()
	} catch {
		Write-Idle
	}
	Start-Sleep -Milliseconds 450
}
