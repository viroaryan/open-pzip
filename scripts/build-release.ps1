param([switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $env:USERPROFILE '.pzip\release-signing.json'
if (-not (Test-Path -LiteralPath $configPath)) {
    throw "Missing $configPath. Create and back up a production signing key first."
}
$signing = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
if (-not (Test-Path -LiteralPath $signing.keystore)) { throw 'Production keystore is missing.' }
$env:PZIP_KEYSTORE_PATH = $signing.keystore
$env:PZIP_KEYSTORE_PASSWORD = $signing.password
$env:PZIP_KEY_ALIAS = $signing.alias
$env:PZIP_KEY_PASSWORD = $signing.password
try {
    $tasks = @(if ($SkipChecks) { ':app:assembleRelease' } else { ':app:testDebugUnitTest'; ':app:lintDebug'; ':app:assembleRelease' })
    & (Join-Path $project 'gradlew.bat') @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
    $out = Join-Path $project 'release-dist'
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $names = @('universal', 'arm64-v8a', 'armeabi-v7a', 'x86_64')
    foreach ($name in $names) {
        $source = Join-Path $project "app\build\outputs\apk\release\app-$name-release.apk"
        if (-not (Test-Path -LiteralPath $source)) { throw "Missing APK: $source" }
        Copy-Item -LiteralPath $source -Destination (Join-Path $out "pzip-$name.apk") -Force
    }
    $lines = foreach ($apk in Get-ChildItem -LiteralPath $out -Filter '*.apk') {
        $hash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $($apk.Name)"
    }
    [System.IO.File]::WriteAllLines((Join-Path $out 'SHA256SUMS.txt'), [string[]]$lines)
    Write-Host "Signed APKs and checksums: $out"
} finally {
    Remove-Item Env:PZIP_KEYSTORE_PATH,Env:PZIP_KEYSTORE_PASSWORD,Env:PZIP_KEY_ALIAS,Env:PZIP_KEY_PASSWORD -ErrorAction SilentlyContinue
}
