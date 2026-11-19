$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$normalizedRoot = $root.Replace('\','/').TrimEnd('/')
$stopped = @()
foreach ($process in Get-CimInstance Win32_Process) {
  $command = $process.CommandLine
  if (-not $command) { continue }
  $owned = $false
  if ($process.Name -eq 'node.exe' -and $command.Contains($root, [StringComparison]::OrdinalIgnoreCase) -and $command -match 'next[\\/]dist[\\/]bin[\\/]next') {
    $owned = $true
  }
  if ($process.Name -eq 'java.exe' -and $command -match 'vn\.vshop\.VShopApplication' -and $command -match '@([^ ]+\.argfile)') {
    $argFile = $Matches[1]
    if (Test-Path -LiteralPath $argFile) {
      $argsText = (Get-Content -LiteralPath $argFile -Raw).Replace('\','/') -replace '/+','/'
      $owned = $argsText.Contains("$normalizedRoot/backend/", [StringComparison]::OrdinalIgnoreCase)
    }
  }
  if ($owned) {
    Stop-Process -Id $process.ProcessId -Force
    $stopped += $process.ProcessId
  }
}
Write-Host "Stopped $($stopped.Count) VShop app process(es). Docker services and data are preserved."
