$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
foreach($line in Get-Content -LiteralPath (Join-Path $root '.env')) {
  if($line -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')}
}
Push-Location $root
try {
  $exists = & docker compose exec -T postgres psql -U vshop -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='vshop_test'"
  if($LASTEXITCODE -ne 0){throw 'PostgreSQL is not ready.'}
  if($exists -ne '1'){
    & docker compose exec -T postgres createdb -U vshop vshop_test
    if($LASTEXITCODE -ne 0){throw 'Cannot create isolated test database.'}
  }
  $env:SPRING_PROFILES_ACTIVE='test'
  $env:DEBUG='false'
  Push-Location backend
  try { & .\mvnw.cmd test; if($LASTEXITCODE -ne 0){throw 'Backend tests failed.'} }
  finally {Pop-Location}
} finally {Pop-Location}
