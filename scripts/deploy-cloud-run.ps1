[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z][a-z0-9-]{4,28}[a-z0-9]$')][string]$ProjectId,
    [ValidatePattern('^[a-z]+-[a-z]+[0-9]$')][string]$Region = 'asia-northeast3',
    [ValidatePattern('^[a-z][a-z0-9-]+$')][string]$Service = 'campus-quest-pvp',
    [string]$Gcloud = 'gcloud',
    [string]$FirebaseCli = 'firebase',
    [switch]$Provision,
    [switch]$Deploy
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
function Invoke-Gcloud([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments) {
    & $Gcloud @Arguments
    if ($LASTEXITCODE -ne 0) { throw "gcloud failed: $($Arguments[0..1] -join ' ')" }
}
$runtimeAccount = "tetris-runtime@$ProjectId.iam.gserviceaccount.com"
$builderAccount = "tetris-builder@$ProjectId.iam.gserviceaccount.com"
$repository = 'tetris'
$image = "$Region-docker.pkg.dev/$ProjectId/$repository/server:$(Get-Date -Format yyyyMMdd-HHmmss)"
Write-Output "Target: $ProjectId / $Region / $Service"
Write-Output 'Cloud Run: 1 vCPU, 1 GiB, min 0, max 1, lifetime CPU, request timeout 3600 seconds.'
Write-Output 'Billing must already be enabled. Default invocation is a plan; use -Provision and/or -Deploy explicitly.'
if (-not $Provision -and -not $Deploy) { return }
$billing = & $Gcloud billing projects describe $ProjectId --format='value(billingEnabled)'
if ($LASTEXITCODE -ne 0 -or $billing.Trim() -ne 'True') { throw 'Link an approved billing account first; this script never enables billing.' }
if ($Provision) {
    Invoke-Gcloud @('services','enable','run.googleapis.com','cloudbuild.googleapis.com','artifactregistry.googleapis.com','firestore.googleapis.com','identitytoolkit.googleapis.com','secretmanager.googleapis.com',"--project=$ProjectId")
    & $Gcloud firestore databases describe '--database=(default)' "--project=$ProjectId" *> $null
    if ($LASTEXITCODE -ne 0) {
        Invoke-Gcloud @('firestore','databases','create','--database=(default)',"--location=$Region",'--type=firestore-native',"--project=$ProjectId")
    }
    foreach ($account in @('tetris-runtime','tetris-builder')) {
        & $Gcloud iam service-accounts describe "$account@$ProjectId.iam.gserviceaccount.com" "--project=$ProjectId" *> $null
        if ($LASTEXITCODE -ne 0) { Invoke-Gcloud @('iam','service-accounts','create',$account,"--project=$ProjectId") }
    }
    foreach ($role in @('roles/datastore.user','roles/firebaseauth.viewer')) {
        Invoke-Gcloud @('projects','add-iam-policy-binding',$ProjectId,"--member=serviceAccount:$runtimeAccount","--role=$role",'--condition=None','--quiet')
    }
    foreach ($role in @('roles/artifactregistry.writer','roles/logging.logWriter','roles/storage.objectViewer')) {
        Invoke-Gcloud @('projects','add-iam-policy-binding',$ProjectId,"--member=serviceAccount:$builderAccount","--role=$role",'--condition=None','--quiet')
    }
    & $Gcloud artifacts repositories describe $repository "--location=$Region" "--project=$ProjectId" *> $null
    if ($LASTEXITCODE -ne 0) { Invoke-Gcloud @('artifacts','repositories','create',$repository,'--repository-format=docker',"--location=$Region","--project=$ProjectId") }
    # Generate only on first provisioning; never rotate an existing administrator token implicitly.
    & $Gcloud secrets describe tetris-admin-token "--project=$ProjectId" *> $null
    if ($LASTEXITCODE -ne 0) {
        $privateDirectory = Join-Path $root '.migration-private'
        [IO.Directory]::CreateDirectory($privateDirectory) > $null
        $secretFile = Join-Path $privateDirectory ('admin-' + [Guid]::NewGuid().ToString('N') + '.tmp')
        $random = New-Object byte[] 32
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $generator.GetBytes($random)
            [IO.File]::WriteAllText($secretFile, [Convert]::ToBase64String($random), (New-Object Text.UTF8Encoding($false)))
            Invoke-Gcloud @('secrets','create','tetris-admin-token','--replication-policy=automatic',"--data-file=$secretFile","--project=$ProjectId")
        } finally {
            $generator.Dispose()
            [Array]::Clear($random, 0, $random.Length)
            if (Test-Path -LiteralPath $secretFile) { Remove-Item -LiteralPath $secretFile -Force }
        }
    }
    Invoke-Gcloud @('secrets','add-iam-policy-binding','tetris-admin-token',"--project=$ProjectId","--member=serviceAccount:$runtimeAccount",'--role=roles/secretmanager.secretAccessor','--quiet')
}
if ($Deploy) {
    # Rules and composite indexes are part of the application, never optional console defaults.
    Invoke-Gcloud @('firestore','databases','describe','--database=(default)',"--project=$ProjectId")
    # Firebase CLI needs the quota header explicitly when reusing gcloud user ADC.
    $previousQuotaProject = $env:GOOGLE_CLOUD_QUOTA_PROJECT
    try {
        $env:GOOGLE_CLOUD_QUOTA_PROJECT = $ProjectId
        & $FirebaseCli deploy --only firestore "--project=$ProjectId" --non-interactive
        if ($LASTEXITCODE -ne 0) { throw 'Firestore rules/index deployment failed. Sign in to Firebase CLI and retry.' }
    } finally { $env:GOOGLE_CLOUD_QUOTA_PROJECT = $previousQuotaProject }
    # Firebase registration and password sign-in are prerequisites for the desktop Auth REST calls.
    $accessToken = & $Gcloud auth print-access-token
    if ($LASTEXITCODE -ne 0) { throw 'Unable to obtain deployment credentials' }
    try {
        $authConfig = Invoke-RestMethod -Uri "https://identitytoolkit.googleapis.com/admin/v2/projects/$ProjectId/config" -Headers @{ Authorization = "Bearer $accessToken"; 'x-goog-user-project' = $ProjectId }
        if (-not $authConfig.signIn.email.enabled -or -not $authConfig.signIn.email.passwordRequired) {
            throw 'Enable Firebase Authentication > Email/Password sign-in before deploying.'
        }
    } finally { $accessToken = $null }
    Invoke-Gcloud @('builds','submit','.',"--project=$ProjectId",'--config=cloudbuild.yaml',"--substitutions=_IMAGE=$image","--service-account=projects/$ProjectId/serviceAccounts/$builderAccount")
    Invoke-Gcloud @('run','deploy',$Service,"--project=$ProjectId","--region=$Region","--image=$image","--service-account=$runtimeAccount",'--allow-unauthenticated','--port=8080','--cpu=1','--memory=1Gi','--min=0','--max=1','--max-instances=1','--concurrency=128','--timeout=3600','--no-cpu-throttling','--no-session-affinity','--set-env-vars',"TETRIS_BACKEND=firebase,FIREBASE_PROJECT_ID=$ProjectId,TETRIS_MAX_WS_CONNECTIONS=128",'--set-secrets=TETRIS_ADMIN_TOKEN=tetris-admin-token:latest','--startup-probe=httpGet.path=/health,initialDelaySeconds=0,timeoutSeconds=3,periodSeconds=5,failureThreshold=24','--quiet')
    $url = & $Gcloud run services describe $Service "--project=$ProjectId" "--region=$Region" --format='value(status.url)'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read deployed service URL' }
    Write-Output "Deployed service: $url"
    Write-Output 'Admission remains in its persisted state. Open it only after import and authenticated two-client verification.'
}
