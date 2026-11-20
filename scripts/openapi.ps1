param([switch]$Update)
# Runs only the OpenAPI contract test against the isolated vshop_test database.
#   (no switch)  fails when docs/api/openapi.json no longer matches the code
#   -Update      rewrites docs/api/openapi.json from the code; review the diff and commit it
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
foreach ($line in Get-Content -LiteralPath (Join-Path $root '.env')) {
  if ($line -match '^([A-Z0-9_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$env:SPRING_PROFILES_ACTIVE = 'test'
$env:DEBUG = 'false'
$env:UPDATE_OPENAPI = if ($Update) { 'true' } else { 'false' }
Push-Location (Join-Path $root 'backend')
try {
  & .\mvnw.cmd -q -B test '-Dtest=OpenApiSpecTests'
  if ($LASTEXITCODE -ne 0) { throw 'OpenAPI contract test failed.' }
} finally { Pop-Location }
if ($Update) { Write-Host 'docs/api/openapi.json regenerated. Run npm run contracts:generate next.' }
