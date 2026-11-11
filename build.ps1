# 音量小白点 —— 免 Gradle 直接编译脚本
# 用法：在 PowerShell 里执行  .\build.ps1
# 依赖：JDK 21 + Android SDK(platforms;android-30, build-tools;35.0.0)

$ErrorActionPreference = "Stop"

$sdk  = "C:\Users\xx-my1-\Android\Sdk"
$bt   = Join-Path $sdk "build-tools\35.0.0"
$jar  = Join-Path $sdk "platforms\android-30\android.jar"
$javaHome = "C:\Program Files\Java\jdk-21"

$proj = Split-Path -Parent $MyInvocation.MyCommand.Path
$b    = Join-Path $proj "build"
Remove-Item -LiteralPath $b -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $b "classes"), (Join-Path $b "dex") | Out-Null

Write-Host "[1/6] aapt2 compile 资源"
& (Join-Path $bt "aapt2.exe") compile --dir (Join-Path $proj "res") -o (Join-Path $b "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败" }

Write-Host "[2/6] aapt2 link 打包资源与清单"
& (Join-Path $bt "aapt2.exe") link -o (Join-Path $b "base.apk") `
    --manifest (Join-Path $proj "AndroidManifest.xml") `
    -I $jar --min-sdk-version 26 --target-sdk-version 30 `
    --version-code 1 --version-name "1.0" (Join-Path $b "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }

Write-Host "[3/6] javac 编译 java"
$srcs = (Get-ChildItem -LiteralPath (Join-Path $proj "src") -Recurse -Filter *.java).FullName
& (Join-Path $javaHome "bin\javac.exe") -encoding UTF-8 -source 8 -target 8 `
    -bootclasspath $jar -d (Join-Path $b "classes") $srcs
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

Write-Host "[4/6] d8 生成 classes.dex"
& (Join-Path $bt "d8.bat") --lib $jar --min-api 26 --output (Join-Path $b "dex") `
    (Get-ChildItem -LiteralPath (Join-Path $b "classes") -Recurse -Filter *.class).FullName
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }

Write-Host "[5/6] 合并 dex + zipalign"
Copy-Item -LiteralPath (Join-Path $b "base.apk") -Destination (Join-Path $b "merged.apk") -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Join-Path $b "merged.apk"), [System.IO.Compression.ZipArchiveMode]::Update)
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
    $zip, (Join-Path $b "dex\classes.dex"), "classes.dex",
    [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
$zip.Dispose()
& (Join-Path $bt "zipalign.exe") -f -p 4 (Join-Path $b "merged.apk") (Join-Path $b "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

Write-Host "[6/6] 签名"
$ks = Join-Path $proj "keystore.jks"
if (-not (Test-Path -LiteralPath $ks)) {
    & (Join-Path $javaHome "bin\keytool.exe") -genkeypair -keystore $ks -alias key `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass android -keypass android -dname "CN=VolDot,O=Local,C=CN"
}
& (Join-Path $bt "apksigner.bat") sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out (Join-Path $proj "音量小白点.apk") (Join-Path $b "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "apksigner 失败" }

Write-Host ("完成： " + (Join-Path $proj "音量小白点.apk"))
