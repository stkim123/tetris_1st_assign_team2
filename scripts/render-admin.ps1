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
if (-not $env:RENDER_ADMIN_TOKEN -or $env:RENDER_ADMIN_TOKEN.Length -lt 24) {
    throw 'Set RENDER_ADMIN_TOKEN in this shell from the Render environment settings.'
}
$headers = @{ Authorization = 'Bearer ' + $env:RENDER_ADMIN_TOKEN }
$base = $ServerUrl.TrimEnd('/')
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
# One operator request, without recurring jobs or tokens in the URL/output.
$response = Invoke-WebRequest -UseBasicParsing -Uri ($base + $path) -Method $method -Headers $headers -MaximumRedirection 0 -TimeoutSec 30
Write-Output $response.Content
