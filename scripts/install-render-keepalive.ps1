# 현재 Windows 사용자의 작업 스케줄러에서 10분마다 상태 요청을 실행한다.
$ErrorActionPreference = 'Stop'
$taskName = 'CampusQuest-RenderKeepalive'
$scriptPath = Join-Path $PSScriptRoot 'render-keepalive.ps1'
if (-not (Test-Path -LiteralPath $scriptPath)) { throw 'render-keepalive.ps1 not found' }
$command = 'powershell.exe -NoProfile -NonInteractive -File "' + $scriptPath + '"'
& schtasks.exe /Create /SC MINUTE /MO 10 /TN $taskName /TR $command /F | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Scheduled task registration failed' }
& schtasks.exe /Run /TN $taskName | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Scheduled task start failed' }
Write-Output ('Registered and started ' + $taskName)
