param(
    [string]$SourceDirectory = 'C:\Project\University_Simulation\sound',
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\audio\university')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime

function Wait-WinRtResult($operation, [type]$resultType) {
    $method = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
            $_.GetParameters().Count -eq 1 -and
            $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } |
        Select-Object -First 1
    $task = $method.MakeGenericMethod($resultType).Invoke($null, @($operation))
    return $task.GetAwaiter().GetResult()
}

function Wait-WinRtAction($operation) {
    $method = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
            $_.GetParameters().Count -eq 1 -and
            $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncActionWithProgress`1' } |
        Select-Object -First 1
    $task = $method.MakeGenericMethod([double]).Invoke($null, @($operation))
    $null = $task.GetAwaiter().GetResult()
}

$resolvedSource = (Resolve-Path -LiteralPath $SourceDirectory).Path
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$resolvedOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path
$profileType = [Windows.Media.MediaProperties.MediaEncodingProfile, Windows.Media.MediaProperties, ContentType=WindowsRuntime]
$transcoderType = [Windows.Media.Transcoding.MediaTranscoder, Windows.Media.Transcoding, ContentType=WindowsRuntime]
$resultType = [Windows.Media.Transcoding.PrepareTranscodeResult, Windows.Media.Transcoding, ContentType=WindowsRuntime]
$profile = $profileType::CreateWav([Windows.Media.MediaProperties.AudioEncodingQuality]::Medium)
$transcoder = [Activator]::CreateInstance($transcoderType)

foreach ($name in @('click', 'nextlog', 'weekSummary', 'background')) {
    $inputPath = Join-Path $resolvedSource ($name + '.mp3')
    $sourceBytes = [IO.File]::ReadAllBytes($inputPath)
    $inputMemory = New-Object IO.MemoryStream(, $sourceBytes)
    $outputMemory = New-Object IO.MemoryStream
    $inputStream = [System.IO.WindowsRuntimeStreamExtensions]::AsRandomAccessStream($inputMemory)
    $outputStream = [System.IO.WindowsRuntimeStreamExtensions]::AsRandomAccessStream($outputMemory)
    try {
        $prepared = Wait-WinRtResult ($transcoder.PrepareStreamTranscodeAsync(
            $inputStream, $outputStream, $profile)) $resultType
        if (-not $prepared.CanTranscode) {
            throw "Cannot transcode $inputPath : $($prepared.FailureReason)"
        }
        Wait-WinRtAction ($prepared.TranscodeAsync())
        $outputPath = Join-Path $resolvedOutput ($name + '.wav')
        [IO.File]::WriteAllBytes($outputPath, $outputMemory.ToArray())
        Get-Item -LiteralPath $outputPath | Select-Object Name, Length, FullName
    } finally {
        $inputStream.Dispose()
        $outputStream.Dispose()
        $inputMemory.Dispose()
        $outputMemory.Dispose()
    }
}
