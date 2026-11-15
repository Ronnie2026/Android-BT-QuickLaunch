param(
  [string]$Notes = "",
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

[xml]$manifest = Get-Content launcher-app\AndroidManifest.xml -Raw
$version = $manifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
if ($version -notmatch '^\d+\.\d+(\.\d+)?$') { throw "Invalid versionName in AndroidManifest.xml: $version" }
$tag = "v$version"
$apk = Join-Path $PSScriptRoot "dist\BT-QuickLaunch-$tag.apk"

if (-not $SkipBuild) {
  Write-Host "==> Building $apk ..." -ForegroundColor Cyan
  & "$PSScriptRoot\build-home.ps1"
  if ($LASTEXITCODE -ne 0) { throw "Build failed" }
}
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

$dirty = git status --porcelain
if ($dirty) {
  throw "Working tree has uncommitted changes. Commit your changes before releasing $tag."
}

$existingTag = git tag -l $tag
if (-not $existingTag) {
  $tagMsg = if ($Notes) { "$tag`: $Notes" } else { "Release $tag" }
  git tag -a $tag -m $tagMsg
  if ($LASTEXITCODE -ne 0) { throw "Failed to create tag $tag" }
}

Write-Host "==> Pushing main and $tag to GitHub (origin)..." -ForegroundColor Cyan
git push origin main $tag
if ($LASTEXITCODE -ne 0) { throw "git push origin failed" }

Write-Host "==> Pushing main and $tag to Gitee (gitee)..." -ForegroundColor Cyan
git push gitee main $tag
if ($LASTEXITCODE -ne 0) { throw "git push gitee failed" }

$releaseNotes = if ($Notes) { $Notes } else { "Release $tag" }

# 1. Publish GitHub Release
Write-Host "==> Publishing GitHub Release $tag ..." -ForegroundColor Cyan
if (-not $env:GH_TOKEN) {
  $cred = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
  $pass = ($cred | Where-Object { $_ -like 'password=*' }) -replace '^password=',''
  if ($pass) { $env:GH_TOKEN = $pass }
}
if ($env:GH_TOKEN) {
  $ghExists = $false
  try { gh release view $tag --repo Ronnie2026/Android-BT-QuickLaunch *> $null; if ($LASTEXITCODE -eq 0) { $ghExists = $true } } catch {}
  if ($ghExists) {
    gh release upload $tag $apk --repo Ronnie2026/Android-BT-QuickLaunch --clobber
  } else {
    gh release create $tag $apk --repo Ronnie2026/Android-BT-QuickLaunch --title $tag --notes $releaseNotes
  }
  if ($LASTEXITCODE -ne 0) { throw "GitHub release failed" }
} else {
  Write-Warning "GH_TOKEN not found; please upload $apk to GitHub Releases manually."
}

# 2. Publish Gitee Release (requires GITEE_TOKEN env var; otherwise opens browser page)
Write-Host "==> Publishing Gitee Release $tag ..." -ForegroundColor Cyan
if ($env:GITEE_TOKEN) {
  $body = @{
    access_token     = $env:GITEE_TOKEN
    tag_name         = $tag
    name             = $tag
    body             = $releaseNotes
    target_commitish = "main"
  }
  $rel = Invoke-RestMethod -Method Post -Uri "https://gitee.com/api/v5/repos/Ronnie2026/Android-BT-QuickLaunch/releases" -Body $body
  if ($rel.id) {
    & curl.exe -f -sS -X POST "https://gitee.com/api/v5/repos/Ronnie2026/Android-BT-QuickLaunch/releases/$($rel.id)/attach_files" `
      -F "access_token=$env:GITEE_TOKEN" `
      -F "file=@$apk" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Gitee APK upload failed" }
    Write-Host "Gitee Release published: https://gitee.com/Ronnie2026/Android-BT-QuickLaunch/releases/tag/$tag" -ForegroundColor Green
  }
} else {
  Write-Warning "GITEE_TOKEN not set. Upload $apk at: https://gitee.com/Ronnie2026/Android-BT-QuickLaunch/releases/new"
}
