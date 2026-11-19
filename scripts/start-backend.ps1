param([switch]$Worker)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
foreach ($line in Get-Content -LiteralPath (Join-Path $root '.env')) {
  if ($line -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$env:SPRING_PROFILES_ACTIVE = if ($Worker) { 'local,worker' } else { 'local' }
$env:DEBUG = 'false'
Push-Location (Join-Path $root 'backend')
try { & .\mvnw.cmd spring-boot:run; if ($LASTEXITCODE -ne 0) { throw "Backend exited: $LASTEXITCODE" } }
finally { Pop-Location }
