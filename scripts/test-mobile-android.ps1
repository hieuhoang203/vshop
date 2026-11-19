param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { Join-Path $root '.local/android-sdk/platform-tools/adb.exe' }
if (-not (Test-Path -LiteralPath $adb)) { throw 'Android SDK/adb is required.' }
$local = Join-Path $root '.local'
$shots = Join-Path $local 'screenshots'
New-Item -ItemType Directory -Path $shots -Force | Out-Null
function Invoke-Adb {
    $output = & $adb -s $Device @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $output" }
    return ($output -join "`n")
}
function Read-Nodes {
    Invoke-Adb shell uiautomator dump /sdcard/vshop-smoke.xml | Out-Null
    Invoke-Adb pull /sdcard/vshop-smoke.xml (Join-Path $local 'native-smoke.xml') | Out-Null
    [xml]$tree = Get-Content -LiteralPath (Join-Path $local 'native-smoke.xml') -Raw
    return $tree.SelectNodes('//node')
}
function Wait-UI([string]$Value, [switch]$Label) {
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    do {
        $matches = @(Read-Nodes | Where-Object { if ($Label) { $_.'content-desc' -eq $Value } else { $_.text -eq $Value } })
        if ($matches.Count) { return $matches[0] }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Native UI did not show: $Value"
}
function Tap-UI([string]$Value, [switch]$Label) {
    $node = Wait-UI $Value -Label:$Label
    if ($node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Invalid UI bounds.' }
    $tapX = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $tapY = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Invoke-Adb shell input tap $tapX $tapY | Out-Null
}
function Save-Screenshot([string]$Name) {
    Invoke-Adb shell screencap -p /sdcard/vshop-native-smoke.png | Out-Null
    Invoke-Adb pull /sdcard/vshop-native-smoke.png (Join-Path $shots "mobile-native-$Name.png") | Out-Null
}

# Run only against the documented local fixture API. No server-side mutations.
$checks = [Collections.Generic.List[string]]::new()
& (Join-Path $PSScriptRoot 'connect-mobile-android.ps1') -Device $Device
Invoke-Adb logcat -c | Out-Null
Invoke-Adb shell am force-stop vn.vshop.mobile | Out-Null
Invoke-Adb shell am start -n vn.vshop.mobile/.MainActivity | Out-Null
Wait-UI 'Chọn giùm mình' -Label | Out-Null
Wait-UI 'Xem Đèn bàn Luma' -Label | Out-Null
Wait-UI 'Xem Túi tote Everyday' -Label | Out-Null
$firstScreen = @(Read-Nodes)
$tabNode = $firstScreen | Where-Object { $_.'content-desc' -eq 'Khám phá' } | Select-Object -First 1
if ($tabNode.bounds -notmatch '^\[\d+,(\d+)\]') { throw 'Cannot locate native tab bar.' }
$tabTop = [int]$Matches[1]
foreach ($pricePattern in @('459.000', '189.000')) {
    $priceNode = $firstScreen | Where-Object { $_.text -match $pricePattern } | Select-Object -First 1
    if (-not $priceNode -or $priceNode.bounds -notmatch '\[\d+,(\d+)\]$' -or [int]$Matches[1] -gt $tabTop) { throw 'Initial native viewport must show both product prices above the tab bar.' }
}
Save-Screenshot 'home'
$checks.Add('Native cold launch and discovery with real catalog')
$checks.Add('First two products and prices visible before scrolling')
Write-Host $checks[-1]

Tap-UI 'Tìm kiếm' -Label
Tap-UI 'Tìm sản phẩm' -Label
Invoke-Adb shell input text Luma | Out-Null
Invoke-Adb shell input keyevent 4 | Out-Null
Wait-UI '1 sản phẩm phù hợp' | Out-Null
Wait-UI 'Đèn bàn Luma' | Out-Null
Save-Screenshot 'search'
$checks.Add('Native search -> API-backed filtered result')
Write-Host $checks[-1]

Tap-UI 'Lọc và sắp xếp' -Label
Wait-UI 'Ngân sách hôm nay?' | Out-Null
Invoke-Adb shell input keyevent 4 | Out-Null
Wait-UI '1 sản phẩm phù hợp' | Out-Null
Tap-UI 'Lọc và sắp xếp' -Label
Tap-UI 'Đến 200k'
Tap-UI 'Áp dụng'
Wait-UI '0 sản phẩm phù hợp' | Out-Null
Wait-UI 'Chưa gặp món hợp gu' | Out-Null
Tap-UI 'Bỏ lọc giá'
Wait-UI '1 sản phẩm phù hợp' | Out-Null
$checks.Add('Native bottom sheet Back handling and real price filter/reset')
Write-Host $checks[-1]

$wasSaved = @(Read-Nodes | Where-Object { $_.'content-desc' -eq 'Bỏ lưu Đèn bàn Luma' }).Count -gt 0
if (-not $wasSaved) { Tap-UI 'Lưu Đèn bàn Luma' -Label }
Wait-UI 'Bỏ lưu Đèn bàn Luma' -Label | Out-Null
Tap-UI 'Đã lưu'
Wait-UI 'Giá khi lưu' | Out-Null
Wait-UI 'Đèn bàn Luma' | Out-Null
Save-Screenshot 'saved'
Invoke-Adb shell am force-stop vn.vshop.mobile | Out-Null
Invoke-Adb shell am start -n vn.vshop.mobile/.MainActivity | Out-Null
Tap-UI 'Đã lưu'
Wait-UI 'Đèn bàn Luma' | Out-Null
$checks.Add('Saved snapshot survives process termination/relaunch')
Write-Host $checks[-1]

Tap-UI 'Xem Đèn bàn Luma' -Label
Wait-UI 'Món hợp gu' | Out-Null
Wait-UI 'Đèn bàn Luma' | Out-Null
Save-Screenshot 'detail'
Invoke-Adb shell input swipe 500 1700 500 650 350 | Out-Null
Wait-UI 'Một chút về món này' | Out-Null
Save-Screenshot 'detail-info'
Invoke-Adb shell input keyevent 4 | Out-Null
if (-not $wasSaved) { Tap-UI 'Bỏ lưu Đèn bàn Luma' -Label }
$checks.Add('Saved card opens current API detail; original favorites restored')
Write-Host $checks[-1]

Invoke-Adb shell am start -a android.intent.action.VIEW -d 'vshop://products/khong-ton-tai' vn.vshop.mobile | Out-Null
Wait-UI 'Món này hiện không còn trong danh mục' | Out-Null
Save-Screenshot 'not-found'
$checks.Add('Deep-link route rejects missing product with genuine 404 state')
Write-Host $checks[-1]
$logs = Invoke-Adb logcat -d -s ReactNativeJS AndroidRuntime
if ($logs -match 'FATAL EXCEPTION|TypeError:|ReferenceError:|Incompatible React versions') { throw 'Native runtime error found; inspect local device logs.' }
$deviceApi = Invoke-Adb shell getprop ro.build.version.sdk
$deviceAbi = Invoke-Adb shell getprop ro.product.cpu.abi
@{ status = 'passed'; device = $Device; platform = "Android API $deviceApi $deviceAbi"; checks = $checks; timestamp = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $local 'native-smoke-report.json') -Encoding utf8
Write-Host 'Native Android smoke passed. No browser/WebView used for these screens.'
