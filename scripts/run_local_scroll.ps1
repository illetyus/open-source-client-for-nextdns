[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidateSet(60, 120)][int]$ExpectedHz,
    [Parameter(Mandatory)][ValidateSet('baseline', 'candidate')][string]$Label,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
    [ValidateSet('warmup', 'none', 'required')][string]$Compilation = 'warmup',
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$PythonPath = 'python'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$adbPath = Join-Path $SdkRoot 'platform-tools/adb.exe'
$aaptPath = Join-Path $SdkRoot 'build-tools/36.0.0/aapt2.exe'
$targetPackage = 'com.aistudio.nextdns.mgrqvt.benchmark'

function Invoke-Device([string[]]$Arguments) {
    $result = & $adbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments[0])" }
    return $result
}

function Get-ApkPackage([string]$Path) {
    $badging = & $aaptPath dump badging $Path 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK manifest could not be read' }
    $match = [regex]::Match(($badging -join "`n"), "(?m)^package: name='([^']+)'")
    if (-not $match.Success) { throw 'APK package is missing' }
    return $match.Groups[1].Value
}

# Explicit paths and package checks prevent touching the user's production install.
$AppApk = (Resolve-Path -LiteralPath $AppApk).Path
$TestApk = (Resolve-Path -LiteralPath $TestApk).Path
if ((Get-ApkPackage $AppApk) -ne $targetPackage) { throw 'Only the isolated .benchmark APK is accepted' }
$testPackage = Get-ApkPackage $TestApk
if ($testPackage -ne 'com.example.macrobenchmark') { throw 'Unexpected instrumentation APK package' }
if ((Invoke-Device @('get-state')) -ne 'device') { throw 'Authorize the USB device first' }
if ((Invoke-Device @('shell', 'getprop', 'ro.kernel.qemu')) -eq '1') { throw 'An emulator cannot certify scrolling' }
$model = (Invoke-Device @('shell', 'getprop', 'ro.product.model')) -join ''
if ($model -notmatch '^SM-G78[01]') { throw 'This acceptance plan requires a Galaxy S20 FE' }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$evidencePath = Join-Path $repoRoot "build/local-scroll/$Label-$ExpectedHz-$stamp"
New-Item -ItemType Directory -Path $evidencePath -Force | Out-Null
$deviceOutput = "/sdcard/Android/media/$testPackage/local-scroll-$stamp"
$metadata = [ordered]@{
    label = $Label; sourceCommit = $SourceCommit; expectedHz = $ExpectedHz
    model = $model; os = (Invoke-Device @('shell', 'getprop', 'ro.build.version.release')) -join ''
    appSha256 = (Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash.ToLowerInvariant()
    testSha256 = (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash.ToLowerInvariant()
    compilation = $Compilation; startedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = 'COLLECTING'; performanceAccepted = $false
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath "$evidencePath/metadata.json" -Encoding utf8
Invoke-Device @('shell', 'dumpsys', 'display') | Set-Content "$evidencePath/display-before.txt"
Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content "$evidencePath/thermal-before.txt"
Invoke-Device @('shell', 'dumpsys', 'battery') | Set-Content "$evidencePath/battery-before.txt"
Invoke-Device @('install', '-r', $AppApk) | Out-Null
Invoke-Device @('install', '-r', $TestApk) | Out-Null
Invoke-Device @('shell', 'mkdir', '-p', $deviceOutput) | Out-Null
$instrumentArgs = @('shell', 'am', 'instrument', '-w', '-r',
    '-e', 'class', 'com.example.macrobenchmark.SteadyScrollBenchmark',
    '-e', 'additionalTestOutputDir', $deviceOutput,
    '-e', 'androidx.benchmark.fullTracing.enable', 'true')
if ($Compilation -eq 'required') { $instrumentArgs += @('-e', 'scroll.profile', 'required') }
if ($Compilation -eq 'none') { $instrumentArgs += @('-e', 'scroll.compilation', 'none') }
$instrumentArgs += "$testPackage/androidx.test.runner.AndroidJUnitRunner"
try {
    $testOutput = Invoke-Device $instrumentArgs
    $testOutput | Set-Content "$evidencePath/instrumentation.txt"
    if (($testOutput -join "`n") -notmatch 'OK \(13 tests\)' -or
        ($testOutput -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        throw 'Scroll instrumentation did not complete all thirteen scenarios'
    }
    $metadata.status = 'INSTRUMENTATION_FINISHED_CAPTURE_NOT_VALIDATED'
} finally {
    Invoke-Device @('pull', $deviceOutput, "$evidencePath/raw") | Out-Null
    Invoke-Device @('shell', 'dumpsys', 'display') | Set-Content "$evidencePath/display-after.txt"
    Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content "$evidencePath/thermal-after.txt"
    $metadata.finishedAt = (Get-Date).ToUniversalTime().ToString('o')
    $metadata | ConvertTo-Json | Set-Content -LiteralPath "$evidencePath/metadata.json" -Encoding utf8
}
& $PythonPath (Join-Path $PSScriptRoot 'verify_scroll_capture.py') $evidencePath
if ($LASTEXITCODE -ne 0) { throw 'Missing or invalid raw benchmark capture; no recorded-success claim' }
$metadata.status = 'RECORDED_REQUIRES_TRACE_REVIEW'
$metadata | ConvertTo-Json | Set-Content -LiteralPath "$evidencePath/metadata.json" -Encoding utf8
# Never turn panel mode or a successful instrumentation exit into a smoothness PASS.
Write-Output "Local evidence: $evidencePath. Review actual frame cadence and paired traces before acceptance."
