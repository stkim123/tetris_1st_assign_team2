param([Parameter(Mandatory=$true)][ValidateSet('Store','Server','Migration')][string]$Check)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$checks=@{
    Store=@('demo-campus-quest','kr.ac.jbnu.se.tetris.firebase.server.FirestoreRankedMatchEmulatorCheck')
    Server=@('demo-cloud-server','kr.ac.jbnu.se.tetris.network.server.FirebaseCloudServerEmulatorCheck')
    Migration=@('demo-campus-migration','kr.ac.jbnu.se.tetris.migration.FirebaseMigrationEmulatorCheck')
}
if (-not $env:FIREBASE_AUTH_EMULATOR_HOST -or -not $env:FIRESTORE_EMULATOR_HOST) {
    throw 'Run through test-firebase-emulators.ps1; both disposable emulators are required.'
}
$env:FIREBASE_PROJECT_ID=$checks[$Check][0]
$env:GOOGLE_CLOUD_PROJECT=$env:FIREBASE_PROJECT_ID
$runtime=(Get-Content -LiteralPath target/runtime-classpath.txt -Raw).Trim()
$classpath="target/classes;target/test-classes;$runtime"
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -ea '-Djava.awt.headless=true' -cp $classpath $checks[$Check][1]
exit $LASTEXITCODE
