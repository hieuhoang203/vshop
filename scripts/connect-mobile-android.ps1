param([string]$Device)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { Join-Path $root '.local/android-sdk/platform-tools/adb.exe' }
if (-not (Test-Path -LiteralPath $adb)) { throw 'Set ANDROID_HOME to an installed Android SDK.' }
$target = if ($Device) { @('-s', $Device) } else { @() }
foreach ($port in @(8081, 8180, 8082)) {
    & $adb @target reverse "tcp:$port" "tcp:$port"
    if ($LASTEXITCODE -ne 0) { throw "Cannot reverse port $port. Connect one USB device/emulator, or pass -Device SERIAL." }
}
Write-Host 'Android localhost now reaches API 8081, Keycloak 8180 and Metro 8082 on this PC.'
