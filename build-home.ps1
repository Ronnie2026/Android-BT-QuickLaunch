$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
. "$PSScriptRoot\resolve-android-toolchain.ps1"
$toolchain = Get-AndroidToolchain
$java = $toolchain.JavaBin
$tools = $toolchain.BuildTools
$android = $toolchain.AndroidJar
[xml]$manifest = Get-Content launcher-app\AndroidManifest.xml -Raw
$version = $manifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
if ($version -notmatch '^\d+\.\d+(\.\d+)?$') { throw 'Invalid application version' }
$apk = "dist\BT-QuickLaunch-v$version.apk"
if ($env:CI -and !(Test-Path .keys\btquicklaunch.jks)) { throw 'CI requires the existing signing key; refusing to generate a replacement.' }
New-Item -ItemType Directory -Force launcher-app\build\classes,launcher-app\build\dex,dist,.keys | Out-Null
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'launcher-app\build\classes') -Filter '*.class' -Recurse | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
$sources=(Get-ChildItem launcher-app\src -Filter '*.java' -Recurse).FullName
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp $android -d launcher-app\build\classes $sources
if($LASTEXITCODE){throw 'Java compilation failed'}
$classes=(Get-ChildItem launcher-app\build\classes -Filter '*.class' -Recurse).FullName
& "$java\java.exe" -cp $toolchain.DexJar com.android.tools.r8.D8 --min-api 26 --lib $android --output launcher-app\build\dex $classes
if($LASTEXITCODE){throw 'DEX compilation failed'}
& "$tools\aapt.exe" package -f -M launcher-app\AndroidManifest.xml -S launcher-app\res -A launcher-app\assets -I $android -F launcher-app\build\unsigned.apk
if($LASTEXITCODE){throw 'Resource packaging failed'}
& "$java\jar.exe" uf launcher-app\build\unsigned.apk -C launcher-app\build\dex classes.dex
if($LASTEXITCODE){throw 'DEX packaging failed'}
& "$tools\zipalign.exe" -f 4 launcher-app\build\unsigned.apk launcher-app\build\aligned.apk
if($LASTEXITCODE){throw 'APK alignment failed'}
if(!(Test-Path .keys\btquicklaunch.jks)){
  & "$java\keytool.exe" -genkeypair -keystore .keys\btquicklaunch.jks -storepass android -keypass android -alias btquicklaunch -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=BT QuickLaunch Local Development'
  if($LASTEXITCODE){throw 'Signing key creation failed'}
}
& "$java\java.exe" -jar "$tools\lib\apksigner.jar" sign --ks .keys\btquicklaunch.jks --ks-pass pass:android --ks-key-alias btquicklaunch --out $apk launcher-app\build\aligned.apk
if($LASTEXITCODE){throw 'APK signing failed'}
& "$java\java.exe" -jar "$tools\lib\apksigner.jar" verify --verbose $apk
if($LASTEXITCODE){throw 'APK signature verification failed'}
Write-Output "Built $apk"
