param([string]$Architectures = 'arm64-v8a,x86_64', [switch]$SkipPrebuild)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$mobileRoot = Join-Path $root 'apps/mobile'
if ($Architectures -notmatch '^(arm64-v8a|x86_64)(,(arm64-v8a|x86_64))*$') { throw 'Use arm64-v8a and/or x86_64.' }
if (-not $env:ANDROID_HOME -and (Test-Path -LiteralPath (Join-Path $root '.local/android-sdk'))) {
    $env:ANDROID_HOME = (Resolve-Path (Join-Path $root '.local/android-sdk')).Path
}
if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME to an installed Android SDK first.' }
if (-not (Test-Path -LiteralPath (Join-Path $mobileRoot '.env.local'))) {
    Copy-Item -LiteralPath (Join-Path $mobileRoot '.env.example') -Destination (Join-Path $mobileRoot '.env.local')
}
# This script deliberately creates a LOCAL, debug-signed APK with an embedded JS bundle.
# Store distributions use EAS production with HTTPS endpoints and private signing credentials.
$env:VSHOP_ALLOW_HTTP = 'true'
$env:EXPO_PUBLIC_ENV = 'development'
$env:VSHOP_BUILD_ENV = 'development'
Push-Location $mobileRoot
try {
    if (-not $SkipPrebuild -or -not (Test-Path -LiteralPath (Join-Path $mobileRoot 'android/gradlew.bat'))) {
        & npx expo prebuild --platform android --no-install
        if ($LASTEXITCODE -ne 0) { throw 'Expo prebuild failed.' }
    }
    [IO.File]::WriteAllText((Join-Path $mobileRoot 'android/local.properties'), "sdk.dir=$($env:ANDROID_HOME.Replace('\','/'))`n")
    & .\android\gradlew.bat -p android :app:assembleRelease "-PreactNativeArchitectures=$Architectures" --console=plain --max-workers=4
    if ($LASTEXITCODE -ne 0) { throw 'Android assembly failed.' }
    $artifacts = Join-Path $root '.local/artifacts'
    New-Item -ItemType Directory -Path $artifacts -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $mobileRoot 'android/app/build/outputs/apk/release/app-release.apk') -Destination (Join-Path $artifacts 'vshop-android-local.apk') -Force
    Write-Host "Local APK: $artifacts/vshop-android-local.apk (debug signed; not a store release)."
} finally { Pop-Location }
