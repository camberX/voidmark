$ErrorActionPreference = "SilentlyContinue"
$ProgressPreference = "SilentlyContinue"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $OutputEncoding
[Console]::InputEncoding = $OutputEncoding

Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]

function Await-Op($op) {
	if ($null -eq $op) {
		return $null
	}
	$args = $op.GetType().GetGenericArguments()
	if ($null -eq $args -or $args.Length -lt 1) {
		return $null
	}
	$method = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
		$_.Name -eq "AsTask" -and $_.IsGenericMethodDefinition -and $_.GetParameters().Count -eq 1
	} | Select-Object -First 1
	if ($null -eq $method) {
		return $null
	}
	$task = $method.MakeGenericMethod($args[0]).Invoke($null, @($op))
	if (-not $task.Wait(4000)) {
		return $null
	}
	return $task.Result
}

function Clean([string]$value) {
	if ([string]::IsNullOrWhiteSpace($value)) {
		return ""
	}
	return (($value -replace "[\t\r\n]+", " ").Trim())
}

function Is-Spotify([string]$id) {
	return -not [string]::IsNullOrWhiteSpace($id) -and $id.ToLowerInvariant().Contains("spotify")
}

function Save-Cover($props, [string]$dest) {
	if ($null -eq $props -or $null -eq $props.Thumbnail) {
		return $false
	}
	try {
		$ras = Await-Op $props.Thumbnail.OpenReadAsync()
		if ($null -eq $ras) {
			return $false
		}
		$size = [int]$ras.Size
		if ($size -lt 24 -or $size -gt 2000000) {
			return $false
		}
		$reader = [Windows.Storage.Streams.DataReader]::new($ras.GetInputStreamAt(0))
		$null = Await-Op ($reader.LoadAsync([uint32]$size))
		$bytes = New-Object byte[] $size
		$reader.ReadBytes($bytes)
		$reader.Dispose()
		[IO.File]::WriteAllBytes($dest, $bytes)
		return $true
	} catch {
		return $false
	}
}

function File-Hash([string]$path) {
	if (-not (Test-Path -LiteralPath $path)) {
		return ""
	}
	$sha = [System.Security.Cryptography.SHA256]::Create()
	try {
		$stream = [IO.File]::OpenRead($path)
		try {
			return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace("-", "")
		} finally {
			$stream.Dispose()
		}
	} finally {
		$sha.Dispose()
	}
}

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync())
$miss = 0
$lastHash = ""
$lastAlbum = ""
$coverPath = Join-Path $env:TEMP "voidmark-smtc-cover.jpg"

while ($true) {
	$sessions = @()
	if ($null -ne $mgr) {
		try {
			$sessions = @($mgr.GetSessions())
		} catch {
			$sessions = @()
		}
	}
	$spotify = @($sessions | Where-Object { Is-Spotify $_.SourceAppUserModelId })
	$chosen = $null
	foreach ($session in $spotify) {
		try {
			if ($session.GetPlaybackInfo().PlaybackStatus.ToString() -eq "Playing") {
				$chosen = $session
				break
			}
		} catch {
		}
	}
	if ($null -eq $chosen) {
		try {
			$current = $mgr.GetCurrentSession()
			if ($null -ne $current -and (Is-Spotify $current.SourceAppUserModelId)) {
				$chosen = $current
			}
		} catch {
		}
	}
	if ($null -eq $chosen -and $spotify.Count -gt 0) {
		$chosen = $spotify[0]
	}

	if ($null -eq $chosen) {
		$miss++
		if ($miss -ge 5) {
			[Console]::Out.WriteLine("FK")
			[Console]::Out.Flush()
			$miss = 0
		}
		Start-Sleep -Milliseconds 500
		continue
	}

	$miss = 0
	try {
		$info = $chosen.GetPlaybackInfo()
		$status = if ($null -eq $info) { "Paused" } else { $info.PlaybackStatus.ToString() }
		$props = Await-Op $chosen.TryGetMediaPropertiesAsync()
		$title = ""
		$artist = ""
		$album = ""
		$albumArtist = ""
		if ($null -ne $props) {
			$title = Clean $props.Title
			$artist = Clean $props.Artist
			$album = Clean $props.AlbumTitle
			$albumArtist = Clean $props.AlbumArtist
		}
		$timeline = $chosen.GetTimelineProperties()
		$position = 0
		$duration = 0
		if ($null -ne $timeline) {
			$position = [int64][Math]::Max(0, $timeline.Position.TotalMilliseconds)
			$duration = [int64][Math]::Max(0, $timeline.EndTime.TotalMilliseconds)
		}
		$cover = ""
		if ($null -ne $props) {
			for ($i = 0; $i -lt 12; $i++) {
				if (Save-Cover $props $coverPath) {
					$hash = File-Hash $coverPath
					if ($hash -ne "" -and ($hash -ne $lastHash -or $album -eq $lastAlbum)) {
						$lastHash = $hash
						$lastAlbum = $album
						$cover = $coverPath
					} elseif ($hash -eq $lastHash -and $album -eq $lastAlbum -and $lastHash -ne "") {
						$cover = $coverPath
					}
					break
				}
			}
		}
		if ([string]::IsNullOrWhiteSpace($title)) {
			[Console]::Out.WriteLine("FK")
		} else {
			[Console]::Out.WriteLine(("FK`t{0}`t{1}`t{2}`t{3}`t{4}`t{5}`t{6}`t{7}" -f $status, $title, $artist, $album, $position, $duration, $albumArtist, $cover))
		}
		[Console]::Out.Flush()
	} catch {
		[Console]::Out.WriteLine("FK")
		[Console]::Out.Flush()
	}
	Start-Sleep -Milliseconds 500
}
