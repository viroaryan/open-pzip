param([string]$Tag = 'v1.0.0')
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$artifacts = Join-Path $project 'release-dist'
$files = @('pzip-universal.apk', 'pzip-arm64-v8a.apk', 'pzip-armeabi-v7a.apk', 'pzip-x86_64.apk', 'SHA256SUMS.txt')
foreach ($name in $files) {
    if (-not (Test-Path -LiteralPath (Join-Path $artifacts $name))) { throw "Missing release asset: $name" }
}
$env:GIT_TERMINAL_PROMPT = '0'
$credential = "protocol=https`nhost=github.com`n`n" | git credential fill
$tokenLine = $credential | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
if (-not $tokenLine) { throw 'GitHub credential unavailable.' }
$token = $tokenLine.Substring(9)
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28'; 'User-Agent' = 'pzip-release-publisher' }
$api = 'https://api.github.com/repos/viroaryan/pzip-releases'
try {
    $release = $null
    try { $release = Invoke-RestMethod -Uri "$api/releases/tags/$Tag" -Headers $headers }
    catch { if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw } }
    if (-not $release) {
        $body = @{
            tag_name = $Tag
            target_commitish = 'main'
            name = "pzip $Tag"
            draft = $true
            prerelease = $false
            generate_release_notes = $false
            body = "pzip 1.0.0 — the first Android release.`n`nBrowse and organize files, create and extract archives, play video and audio, review storage cleanup suggestions, and use optional privacy tools.`n`nChoose the universal APK for most phones, or an architecture-specific APK for a smaller download. Verify the SHA-256 hash with SHA256SUMS.txt. Android 8.0 or newer is required.`n`nSource and build instructions: https://github.com/viroaryan/open-pzip"
        } | ConvertTo-Json -Depth 4
        $release = Invoke-RestMethod -Method Post -Uri "$api/releases" -Headers $headers -ContentType 'application/json' -Body $body
        Write-Output "Created draft release $Tag"
    }
    $baseUpload = $release.upload_url.Split('{')[0]
    foreach ($name in $files) {
        $path = Join-Path $artifacts $name
        $existing = @($release.assets | Where-Object { $_.name -eq $name })
        if ($existing.Count -gt 0) {
            if ($existing[0].size -ne (Get-Item -LiteralPath $path).Length) { throw "Existing asset $name differs in size. Resolve it before republishing." }
            Write-Output "Already uploaded: $name"
            continue
        }
        $mime = if ($name.EndsWith('.apk')) { 'application/vnd.android.package-archive' } else { 'text/plain' }
        $uploadUrl = "${baseUpload}?name=$([uri]::EscapeDataString($name))"
        $uploaded = Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $headers -ContentType $mime -InFile $path -TimeoutSec 600
        if ($uploaded.state -ne 'uploaded') { throw "Upload did not complete: $name" }
        Write-Output "Uploaded: $name"
    }
    if ($release.draft) {
        $published = Invoke-RestMethod -Method Patch -Uri "$api/releases/$($release.id)" -Headers $headers -ContentType 'application/json' -Body '{"draft":false,"make_latest":"true"}'
        Write-Output "Published: $($published.html_url)"
    } else { Write-Output "Release already public: $($release.html_url)" }
} finally {
    $token = $null
    Remove-Item Env:GIT_TERMINAL_PROMPT -ErrorAction SilentlyContinue
}
