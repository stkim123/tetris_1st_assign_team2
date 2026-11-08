param(
    [string]$PublicConfigPath = '',
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskOut = Join-Path $taskRoot 'out'
$taskJar = Join-Path $taskOut 'tetris.jar'
if (-not $PublicConfigPath) {
    $PublicConfigPath = Join-Path $taskRoot 'tetris-client.properties'
    if (-not (Test-Path -LiteralPath $PublicConfigPath)) {
        $PublicConfigPath = Join-Path $taskRoot 'src\main\resources\online-defaults.properties'
    }
}
if (-not (Test-Path -LiteralPath $PublicConfigPath -PathType Leaf)) {
    throw 'Public client configuration file does not exist.'
}
$allowedFields = @('online.provider', 'server.url', 'firebase.projectId',
    'firebase.apiKey', 'supabase.url', 'supabase.publishableKey')
$publicValues = @{}
foreach ($line in [IO.File]::ReadAllLines($PublicConfigPath)) {
    if ($line -match '^\s*[#!]' -or -not $line.Trim()) { continue }
    if ($line -notmatch '^\s*([A-Za-z][A-Za-z0-9._-]*)\s*=\s*(.*?)\s*$') {
        throw 'Invalid public client configuration line.'
    }
    $field = $Matches[1]
    $fieldValue = $Matches[2].Trim()
    if ($allowedFields -cnotcontains $field -or -not $fieldValue -or
        $publicValues.ContainsKey($field)) {
        throw 'Unexpected, empty, or duplicate public client configuration field.'
    }
    $publicValues[$field] = $fieldValue
}
if (-not $publicValues.ContainsKey('server.url')) { throw 'Missing public server URL.' }
$serverUri = $null
if (-not [Uri]::TryCreate($publicValues['server.url'], [UriKind]::Absolute, [ref]$serverUri) -or
    $serverUri.Scheme -ne 'wss' -or -not $serverUri.Host -or
    $serverUri.AbsolutePath -ne '/ws' -or $serverUri.UserInfo -or
    $serverUri.Query -or $serverUri.Fragment) {
    throw 'Server URL must be a credential-free WSS /ws endpoint.'
}
$provider = if ($publicValues.ContainsKey('online.provider')) {
    $publicValues['online.provider']
} elseif ($publicValues.ContainsKey('firebase.projectId') -or
          $publicValues.ContainsKey('firebase.apiKey')) {
    'firebase'
} else {
    'supabase'
}
if ($provider -cne 'firebase' -and $provider -cne 'supabase') {
    throw 'Unknown public online provider.'
}
if ($provider -ceq 'firebase') {
    $expectedFields = @('online.provider', 'server.url', 'firebase.projectId', 'firebase.apiKey')
    if ($publicValues.Count -ne 4 -or
        @($expectedFields | Where-Object { -not $publicValues.ContainsKey($_) }).Count -ne 0) {
        throw 'Firebase packages require only provider, server URL, project ID, and Web API key.'
    }
    if ($publicValues['firebase.projectId'] -cnotmatch '^[a-z][a-z0-9-]{3,62}$' -or
        $publicValues['firebase.apiKey'] -cnotmatch '^AIza[A-Za-z0-9_-]{35}$') {
        throw 'Invalid Firebase public project ID or Web API key.'
    }
    $configLines = @('online.provider=firebase',
        ('server.url=' + $publicValues['server.url']),
        ('firebase.projectId=' + $publicValues['firebase.projectId']),
        ('firebase.apiKey=' + $publicValues['firebase.apiKey']))
    $coldStartLine = 'Cloud Run 서버는 유휴 시 인스턴스가 내려가 첫 연결이 지연될 수 있습니다. 대전 접수 상태는 Firestore에서 복원합니다.'
    $publicConfigLine = '공개 클라이언트 설정에는 서버 주소와 Firebase Web API key만 포함되며 계정 토큰은 저장하지 않습니다.'
} else {
    $expectedFields = @('server.url', 'supabase.url', 'supabase.publishableKey')
    if ($publicValues.ContainsKey('online.provider')) { $expectedFields += 'online.provider' }
    if ($publicValues.Count -ne $expectedFields.Count -or
        @($expectedFields | Where-Object { -not $publicValues.ContainsKey($_) }).Count -ne 0) {
        throw 'Supabase packages require only server URL, project URL, and publishable key.'
    }
    $databaseUri = $null
    if (-not [Uri]::TryCreate($publicValues['supabase.url'], [UriKind]::Absolute, [ref]$databaseUri) -or
        $databaseUri.Scheme -ne 'https' -or -not $databaseUri.Host -or
        ($databaseUri.AbsolutePath -ne '/' -and $databaseUri.AbsolutePath -ne '') -or
        $databaseUri.UserInfo -or $databaseUri.Query -or $databaseUri.Fragment) {
        throw 'Supabase project URL must use credential-free HTTPS.'
    }
    $publicKey = $publicValues['supabase.publishableKey']
    if ($publicKey -cnotmatch '^sb_publishable_[A-Za-z0-9_-]{12,}$') {
        if ($publicKey -cnotmatch '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$') {
            throw 'Only a publishable or anon key is allowed.'
        }
        $segments = $publicKey.Split('.')
        $payload = $segments[1].Replace('-', '+').Replace('_', '/')
        $payload += '=' * ((4 - $payload.Length % 4) % 4)
        try {
            $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
        } catch {
            throw 'Only a publishable or anon key is allowed.'
        }
        if ($claims.role -cne 'anon') { throw 'Only a publishable or anon key is allowed.' }
    }
    $configLines = @()
    if ($publicValues.ContainsKey('online.provider')) { $configLines += 'online.provider=supabase' }
    $configLines += @(('server.url=' + $publicValues['server.url']),
        ('supabase.url=' + $publicValues['supabase.url']),
        ('supabase.publishableKey=' + $publicKey))
    $coldStartLine = 'Render Free 서버는 유휴 시 절전되어 첫 연결이 지연될 수 있습니다. 대전 접수 상태는 서버가 DB에서 복원합니다.'
    $publicConfigLine = '공개 클라이언트 설정에는 서버 주소와 Supabase publishable key만 포함되며 계정 토큰은 저장하지 않습니다.'
}
if ($ValidateOnly) {
    Write-Output "Public client configuration valid: $provider"
    return
}
if (-not (Test-Path -LiteralPath $taskJar -PathType Leaf)) { throw 'Build the game before packaging.' }
$packageRoot = Join-Path $taskOut 'campus-quest-client'
New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
Copy-Item -LiteralPath $taskJar -Destination (Join-Path $packageRoot 'tetris.jar') -Force
$jarConfig = Join-Path $taskOut 'tetris-client.properties'
[IO.File]::WriteAllLines($jarConfig, $configLines, [Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $jarConfig -Destination (Join-Path $packageRoot 'tetris-client.properties') -Force
$launcher = @'
@echo off
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" (
  start "" "%JAVA_HOME%\bin\javaw.exe" -jar "%~dp0tetris.jar"
  exit /b
)
where javaw.exe >nul 2>nul
if not errorlevel 1 (
  start "" javaw.exe -jar "%~dp0tetris.jar"
  exit /b
)
echo Java 8 or newer is required. Install a Java runtime, then run play.cmd again.
pause
'@
[IO.File]::WriteAllText((Join-Path $packageRoot 'play.cmd'), $launcher, [Text.Encoding]::ASCII)
$readme = @'
Tetris Monster / Campus Quest

Java 8 이상을 설치하고 play.cmd 또는 tetris.jar를 실행하세요.
대학교 → 졸업 → 취업의 3개 스테이지, 총 9전투입니다.
금빛 균열이 있는 콘크리트 블록 칸이 포함된 줄을 지우면 아이템을 채굴합니다.
전투는 직접 돌진·접촉 타격과 피격 모션으로 표시됩니다.
로컬 시작: 계정 없이 스토리·튜토리얼·연습·상점 이용.
온라인 로그인: 아이디/비밀번호로 가입 또는 로그인 → Online Battle → 방 생성/입장 → 두 사람 READY.
아이디는 영문자로 시작하는 영문/숫자/밑줄 3~20자입니다.
PvP 랭킹: 서버가 확정한 공식 경기 전적만 표시. 로컬 PvE 기록은 제외.
아이디 계정은 이메일이 없으므로 비밀번호 셀프 복구가 불가능합니다.
__COLD_START__
온라인 아이템 채굴 규칙은 업데이트된 서버와 접속할 때 적용됩니다.

조작: ←→ 이동 / ↑↓ 회전 / D 한 칸 낙하 / SPACE 즉시 낙하 / C HOLD
P 로컬 일시정지 / 1~4 아이템 / ESC 돌아가기
로비의 설정에서 BGM/효과음 음량·음소거와 5단계 조작 튜토리얼을 설정합니다.
첫 로컬 시작에서 튜토리얼을 직접 진행하며, 설정의 '다시 보지 않기'를 저장할 수 있습니다.
창은 크기를 조절할 수 있으며 플레이 영역 비율을 4:3으로 유지합니다.

진행·캐릭터·재화·소리·튜토리얼 선택은 사용자 홈 .tetris-monster에 저장됩니다.
기존 진행은 v2로 이관되며 첫 저장 전 .v1.bak 백업을 남깁니다.
자체 제작 캠퍼스·블록·전투 포즈·몬스터 이미지와 University_Simulation 차용 이미지/효과음은 JAR에 포함됩니다.
__PUBLIC_CONFIG__
'@
$readme = $readme.Replace('__COLD_START__', $coldStartLine).Replace('__PUBLIC_CONFIG__', $publicConfigLine)
[IO.File]::WriteAllText((Join-Path $packageRoot 'README.txt'), $readme, [Text.UTF8Encoding]::new($true))
$zip = Join-Path $taskOut 'campus-quest-client.zip'
# 폴더의 다른 파일이 섞이지 않도록 배포 파일을 명시한다.
$files = @('tetris.jar', 'tetris-client.properties', 'play.cmd', 'README.txt') | ForEach-Object { Join-Path $packageRoot $_ }
Compress-Archive -LiteralPath $files -DestinationPath $zip -Force
Write-Output "Client package: $zip"
Get-FileHash -LiteralPath $zip -Algorithm SHA256 | Select-Object Algorithm, Hash
