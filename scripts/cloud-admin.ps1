param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [Parameter(Mandatory=$true)][ValidateSet('status','drain','open','recover')][string]$Action,
    [string]$StoppedRunId = '',
    [switch]$StoppedRunConfirmed
)
$ErrorActionPreference = 'Stop'
$server = [Uri]$ServerUrl
if (-not $server.IsAbsoluteUri -or $server.UserInfo -or $server.Query -or $server.Fragment -or $server.AbsolutePath -ne '/') {
    throw 'Use the base server URL without path, credentials, query or fragment.'
}
if ($server.Scheme -ne 'https' -and -not ($server.Scheme -eq 'http' -and $server.IsLoopback)) {
    throw 'HTTPS is required except for a local test server.'
}
if (-not $env:TETRIS_ADMIN_TOKEN -or $env:TETRIS_ADMIN_TOKEN.Length -lt 24) {
    throw 'Set TETRIS_ADMIN_TOKEN in this process from Secret Manager. Never paste it into chat.'
}
$headers = @{ Authorization = 'Bearer ' + $env:TETRIS_ADMIN_TOKEN }
$path = '/admin/' + $Action
$method = 'POST'
if ($Action -eq 'status') { $method = 'GET' }
if ($Action -eq 'recover') {
    $parsedRun = [Guid]::Empty
    if (-not $StoppedRunConfirmed -or -not [Guid]::TryParse($StoppedRunId, [ref]$parsedRun)) {
        throw 'Recover only a confirmed stopped run: -StoppedRunId <UUID> -StoppedRunConfirmed.'
    }
    $path = '/admin/recover-stopped-run?runId=' + $parsedRun.ToString()
}
$response = Invoke-WebRequest -UseBasicParsing -Uri ($ServerUrl.TrimEnd('/') + $path) -Method $method -Headers $headers -MaximumRedirection 0 -TimeoutSec 30
Write-Output $response.Content
