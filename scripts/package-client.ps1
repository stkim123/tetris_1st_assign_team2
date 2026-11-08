param(
    [switch]$SkipBuild,
    [string]$PublicConfigPath = ''
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

# This older ZIP format ships an example file. Generate it only from validated
# public fields so a stale Render example or a secret-bearing comment cannot enter it.
$defaults = Join-Path $root 'src/main/resources/online-defaults.properties'
$selectedConfig = if ($PublicConfigPath) { $PublicConfigPath } else { $defaults }
$validation = & (Join-Path $PSScriptRoot 'package-campus-client.ps1') -ValidateOnly -PublicConfigPath $selectedConfig
if ($validation -eq 'Public client configuration valid: firebase') {
    $provider = 'firebase'
    $fields = @('online.provider', 'server.url', 'firebase.projectId', 'firebase.apiKey')
} elseif ($PublicConfigPath -and $validation -eq 'Public client configuration valid: supabase') {
    $provider = 'supabase'
    $fields = @('online.provider', 'server.url', 'supabase.url', 'supabase.publishableKey')
} else {
    throw 'Default client packaging requires Firebase; Supabase needs an explicit public config path.'
}
$values = @{}
foreach ($line in [IO.File]::ReadAllLines($selectedConfig)) {
    if ($line -match '^\s*([A-Za-z][A-Za-z0-9._-]*)\s*=\s*(.*?)\s*$') {
        $values[$Matches[1]] = $Matches[2].Trim()
    }
}
$exampleLines = @()
foreach ($field in $fields) {
    if ($values.ContainsKey($field)) { $exampleLines += ($field + '=' + $values[$field]) }
}

if (-not $SkipBuild) { & (Join-Path $root 'build.ps1') -Task Test }
$jar = Join-Path $root 'out/tetris.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the verified client JAR first.' }
$staging = Join-Path $root 'out/client-package'
New-Item -ItemType Directory -Path $staging -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $staging 'tetris.jar') -Force
[IO.File]::WriteAllLines((Join-Path $staging 'tetris-client.properties.example'),
    $exampleLines, [Text.UTF8Encoding]::new($false))
$launch = @'
@echo off
cd /d "%~dp0"
start "Tetris" javaw -jar "%~dp0tetris.jar"
'@
[IO.File]::WriteAllText((Join-Path $staging 'play.cmd'), $launch, [Text.Encoding]::ASCII)
$onlineConfigLine = if ($provider -eq 'firebase') {
    '기본 온라인 설정은 Firebase Auth와 서울 리전 Cloud Run 서버로 JAR에 포함됩니다. Cloud Run이 유휴 후 시작될 때 첫 접속이 지연될 수 있습니다.'
} else {
    '이 ZIP은 명시적으로 선택한 Supabase 공개 설정 예시를 포함합니다. JAR의 내장 기본값은 Firebase이므로 Supabase 연결을 원하면 예시 파일을 tetris-client.properties로 복사하세요.'
}
$readme = @'
Tetris (Java 8 이상 필요)

play.cmd 또는 java -jar tetris.jar로 실행합니다.
__ONLINE_CONFIG__
예시의 공개 설정을 적용하려면 tetris-client.properties.example을
tetris-client.properties로 복사해 JAR 옆에 둡니다.
운영자 secret key, admin token, 서비스 계정 키는 이 폴더에 넣지 않습니다.

온라인: 아이디/비밀번호 가입 또는 로그인 후 Online Battle 접속.
첫 참가자가 방을 만들고 방 번호를 상대에게 전달하면 두 명이 준비 후 대전합니다.
저장이 완료된 경기만 랭킹에 반영됩니다. 재실행 시 다시 로그인합니다.
아이디 계정은 이메일 주소가 없어 비밀번호 셀프 복구를 제공하지 않습니다.
튜토리얼·스토리·로컬 연습 기록은 공식 랭킹에 반영되지 않습니다.
'@
$readme = $readme.Replace('__ONLINE_CONFIG__', $onlineConfigLine)
[IO.File]::WriteAllText((Join-Path $staging 'README.txt'), $readme, (New-Object Text.UTF8Encoding($true)))
# 명시한 공개 파일만 압축. 이전 실행의 로컬 설정이나 임의 파일은 포함하지 않음.
$files = @('tetris.jar','tetris-client.properties.example','play.cmd','README.txt') | ForEach-Object { Join-Path $staging $_ }
$archive = Join-Path $root 'out/tetris-client.zip'
Compress-Archive -LiteralPath $files -DestinationPath $archive -Force
Get-FileHash -LiteralPath $archive -Algorithm SHA256 | Select-Object Path, Hash
