$ErrorActionPreference = 'SilentlyContinue'
$utf8 = New-Object System.Text.UTF8Encoding $false
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$stdout = [Console]::OpenStandardOutput()

function Write-Fk([string]$text) {
	$bytes = $utf8.GetBytes($text + "`n")
	$stdout.Write($bytes, 0, $bytes.Length)
	$stdout.Flush()
}

Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction SilentlyContinue
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null

function Await-Op($operation, [Type]$resultType) {
	if ($null -eq $operation) { return $null }
	$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |
		Where-Object {
			$_.Name -eq 'AsTask' -and
			$_.GetParameters().Count -eq 1 -and
			$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
		} |
		Select-Object -First 1
	if ($null -eq $asTask) { return $null }
	$task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
	if (-not $task.Wait(8000)) { return $null }
	return $task.Result
}

function Find-Spotify($manager) {
	if ($null -eq $manager) { return $null }
	$matches = @()
	try {
		foreach ($session in $manager.GetSessions()) {
			if (([string]$session.SourceAppUserModelId) -match 'Spotify') {
				$matches += $session
			}
		}
	} catch {}
	foreach ($session in $matches) {
		try {
			if ([string]$session.GetPlaybackInfo().PlaybackStatus -eq 'Playing') {
				return $session
			}
		} catch {}
	}
	try {
		$current = $manager.GetCurrentSession()
		if ($null -ne $current -and ([string]$current.SourceAppUserModelId) -match 'Spotify') {
			return $current
		}
	} catch {}
	if ($matches.Count -gt 0) { return $matches[0] }
	return $null
}

function File-Hash([string]$path) {
	try {
		if ([string]::IsNullOrEmpty($path) -or -not (Test-Path -LiteralPath $path)) { return '' }
		return [string](Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
	} catch {
		return ''
	}
}

function Clean([string]$value) {
	if ([string]::IsNullOrEmpty($value)) { return '' }
	return ($value -replace '[\t\r\n]', ' ')
}

function Save-Cover($info, $dest) {
	try {
		if ($null -eq $info -or $null -eq $info.Thumbnail) { return '' }
		$ras = Await-Op ($info.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
		if ($null -eq $ras -or $ras.Size -le 0) { return '' }
		$net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($ras)
		try {
			$fs = [System.IO.File]::Create($dest)
			try {
				$net.CopyTo($fs)
				$fs.Flush()
			} finally {
				$fs.Dispose()
			}
		} finally {
			$net.Dispose()
			try { $ras.Dispose() } catch {}
		}
		if ((Test-Path -LiteralPath $dest) -and ((Get-Item -LiteralPath $dest).Length -gt 64)) {
			return $dest
		}
	} catch {}
	return ''
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$manager = Await-Op ($managerType::RequestAsync()) $managerType
$misses = 0
$coverKey = ''
$coverFile = ''
$coverPending = ''
$coverReady = $false
$coverTries = 0
$lastCoverHash = ''
$lastCoverAlbum = ''

while ($true) {
	if ($null -eq $manager) {
		$manager = Await-Op ($managerType::RequestAsync()) $managerType
	}
	$session = Find-Spotify $manager
	if ($null -eq $session) {
		$misses++
		if ($misses -ge 5) {
			Write-Fk 'FK'
			$misses = 0
			$coverKey = ''
			$coverFile = ''
			$coverPending = ''
			$coverReady = $false
			$coverTries = 0
		}
	} else {
		$misses = 0
		try {
			$info = Await-Op ($session.TryGetMediaPropertiesAsync()) $propsType
			if ($null -ne $info) {
				$timeline = $session.GetTimelineProperties()
				$status = [string]$session.GetPlaybackInfo().PlaybackStatus
				$pos = [int64][Math]::Max(0, $timeline.Position.TotalMilliseconds)
				$dur = [int64][Math]::Max(0, $timeline.EndTime.TotalMilliseconds)
				$title = Clean ([string]$info.Title)
				$artist = Clean ([string]$info.Artist)
				$album = Clean ([string]$info.AlbumTitle)
				$albumArtist = Clean ([string]$info.AlbumArtist)
				$trackKey = "$title|$artist|$album"
				if ($trackKey -ne $coverKey) {
					$coverKey = $trackKey
					$coverReady = $false
					$coverTries = 0
					$coverFile = ''
					if (-not [string]::IsNullOrEmpty($coverPending) -and (Test-Path -LiteralPath $coverPending)) {
						Remove-Item -LiteralPath $coverPending -Force -ErrorAction SilentlyContinue
					}
					$coverPending = Join-Path $env:TEMP ("voidmark-smtc-" + [guid]::NewGuid().ToString('N') + ".jpg")
				}
				if (-not $coverReady -and $coverTries -lt 12) {
					$coverTries++
					$saved = Save-Cover $info $coverPending
					if (-not [string]::IsNullOrEmpty($saved)) {
						$hash = File-Hash $saved
						$sameAlbum = (-not [string]::IsNullOrEmpty($album)) -and ($album -eq $lastCoverAlbum)
						if ($hash -and (($hash -ne $lastCoverHash) -or $sameAlbum)) {
							$coverReady = $true
							$coverFile = $saved
							$lastCoverHash = $hash
							$lastCoverAlbum = $album
						}
					}
				}
				$outCover = ''
				if ($coverReady) { $outCover = $coverFile }
				Write-Fk (('FK{0}{1}{0}{2}{0}{3}{0}{4}{0}{5}{0}{6}{0}{7}{0}{8}') -f [char]9, $status, $title, $artist, $album, $pos, $dur, $albumArtist, (Clean $outCover))
			}
		} catch {}
	}
	Start-Sleep -Milliseconds 500
}
