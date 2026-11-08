param(
    [ValidateSet('Build', 'Test', 'Run', 'CloudServer', 'GuiTest', 'Preview', 'NetworkFixture')]
    [string]$Task = 'Build',
    [string]$JdkHome = '',
    [switch]$AllowVisibleDesktop
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot

# 개발자 검증의 창·포커스·OS 입력 사용 제외 및 명시적 옵션을 통한 수동 GUI 검증 허용
if (($Task -eq 'GuiTest' -or $Task -eq 'Preview') -and -not $AllowVisibleDesktop) {
    throw 'This task opens visible windows. Use -Task Test for headless verification; manual desktop testing requires -AllowVisibleDesktop.'
}

function Find-Jdk {
    if ($JdkHome) {
        if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\javac.exe'))) {
            throw "No javac.exe in requested JDK: $JdkHome"
        }
        return (Resolve-Path -LiteralPath $JdkHome).Path
    }
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $compiler = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($compiler) { $candidates += Split-Path (Split-Path $compiler.Source) }
    foreach ($base in @("$env:USERPROFILE\.jdks", 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft')) {
        if (Test-Path -LiteralPath $base) {
            $candidates += @(Get-ChildItem -LiteralPath $base -Directory | ForEach-Object FullName)
        }
    }
    # 기존 검증용 로컬 JDK 탐색 및 다운로드·설치 제외
    $candidates += Join-Path $env:TEMP 'codex-tetris-baseline-jdk8\jdk8u504-b01'
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) { return $candidate }
    }
    throw 'JDK 8+ required. Set JAVA_HOME or pass -JdkHome <path>. A JRE alone can run the JAR but cannot build it.'
}

# Maven Wrapper로 의존성과 별도 배포물을 재현하며 기존 명령 진입점 유지
$selectedJdk = Find-Jdk
$java = Join-Path $selectedJdk 'bin\java.exe'
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $selectedJdk
    Write-Output "JDK: $selectedJdk"
    # 제거된 리소스/클래스가 이전 target/에 남아 JAR로 다시 들어가지 않도록 매번 깨끗이 빌드
    $mavenArguments = @('-B', '-ntp', 'clean', 'package')
    if ($Task -notin @('Test', 'GuiTest')) { $mavenArguments += '-DskipTests=true' }
    # Windows PowerShell 5는 리다이렉트된 정상 stderr 로그도 NativeCommandError로 취급하므로 종료 코드로 판정
    $savedErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & (Join-Path $projectRoot 'mvnw.cmd') @mavenArguments
        $mavenExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedErrorPreference }
    if ($mavenExitCode -ne 0) { throw "Maven build failed (exit $mavenExitCode)" }
} finally { $env:JAVA_HOME = $previousJavaHome }

New-Item -ItemType Directory -Path (Join-Path $projectRoot 'out') -Force | Out-Null
$jarPath = Join-Path $projectRoot 'out\tetris.jar'
$serverJar = Join-Path $projectRoot 'out\tetris-server.jar'
Copy-Item -LiteralPath (Join-Path $projectRoot 'target\tetris-client.jar') -Destination $jarPath -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'target\tetris-server.jar') -Destination $serverJar -Force
Write-Output "Client JAR: $jarPath"
Write-Output "Server JAR: $serverJar"
$dependencies = [IO.File]::ReadAllText((Join-Path $projectRoot 'target\runtime-classpath.txt')).Trim()
$testClasspath = (Join-Path $projectRoot 'target\classes') + ';' + (Join-Path $projectRoot 'target\test-classes') + ';' + $dependencies

if ($Task -eq 'Preview') {
    & $java '-Djava.awt.headless=false' -cp $testClasspath kr.ac.jbnu.se.tetris.ui.UiPreviewMain
} elseif ($Task -eq 'NetworkFixture') {
    & $java '-Djava.awt.headless=true' -ea -cp $testClasspath kr.ac.jbnu.se.tetris.support.OnlinePreviewScenario
} elseif ($Task -eq 'GuiTest') {
    & $java '-Djava.awt.headless=false' -ea -cp $testClasspath kr.ac.jbnu.se.tetris.ui.seongeun.BoardKeyboardDesktopSmoke
} elseif ($Task -eq 'Run') {
    & (Join-Path $selectedJdk 'bin\javaw.exe') -jar $jarPath
} elseif ($Task -eq 'CloudServer') {
    & $java '-Djava.awt.headless=true' '-Xmx256m' -jar $serverJar
}
if ($LASTEXITCODE -ne 0) { throw "Task failed: $Task (exit $LASTEXITCODE)" }
