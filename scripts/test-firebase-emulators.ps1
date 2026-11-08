[CmdletBinding()]
param(
    [ValidateSet('All','Store','Server','Migration')][string]$Check='All',
    [string]$JdkHome=$env:JAVA_HOME,
    [string]$FirebaseCli=''
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin/java.exe'))) {
    throw 'Supply a Java 21+ JDK with -JdkHome for the current Firestore emulator.'
}
if (-not $FirebaseCli) {
    $localCli=Join-Path $root '.tools/firebase-cli/node_modules/.bin/firebase.cmd'
    $FirebaseCli=if (Test-Path -LiteralPath $localCli) { $localCli } else { 'firebase' }
}
$env:JAVA_HOME=$JdkHome
$env:PATH="$JdkHome\bin;$env:PATH"
$env:CI='true'
& .\mvnw.cmd -B -ntp -DskipTests test-compile
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
$projects=@{ Store='demo-campus-quest'; Server='demo-cloud-server'; Migration='demo-campus-migration' }
$selected=if ($Check -eq 'All') { @('Store','Server','Migration') } else { @($Check) }
foreach ($suite in $selected) {
    # Each invocation starts a fresh in-memory Auth/Firestore pair and automatically stops it.
    # CLI --project also selects the audience of unsigned emulator Auth tokens.
    $command="powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-firebase-check.ps1 -Check $suite"
    & $FirebaseCli emulators:exec --only auth,firestore --project $projects[$suite] --non-interactive $command
    if ($LASTEXITCODE -ne 0) { throw "Firebase $suite integration failed" }
}
Write-Output 'PASS: Firebase store, server, and migration checks selected above.'
