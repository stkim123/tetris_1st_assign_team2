# Render Free의 유휴 정지를 줄이기 위한 외부 상태 요청. 무료 플랜의 재시작/장애까지 막지는 못한다.
$ErrorActionPreference = 'Stop'
$base = 'https://tetris-ranked-pvp.onrender.com'
$healthCode = 0
$intakeCode = 0

function Get-HttpCode([string]$url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -Method Get -TimeoutSec 120
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        return 0
    }
}

$healthCode = Get-HttpCode ($base + '/healthz')
$intakeCode = Get-HttpCode ($base + '/ws')
$status = [ordered]@{
    checkedUtc = [DateTime]::UtcNow.ToString('o')
    healthHttp = $healthCode
    intakeHttp = $intakeCode
    healthy = ($healthCode -eq 200)
    intakeOpen = ($intakeCode -eq 401)
}
$outDir = Join-Path $PSScriptRoot '..\out'
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$status | ConvertTo-Json -Compress | Set-Content -Path (Join-Path $outDir 'render-keepalive-status.json') -Encoding UTF8
Write-Output ('Render health={0} intake={1}' -f $healthCode, $intakeCode)
# 접수 닫힘은 별도 운영 상태이며, 서버가 살아 있으면 keepalive 자체는 성공이다.
if ($healthCode -ne 200) { exit 1 }
