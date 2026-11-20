$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
if (Test-Path -LiteralPath $envFile) {
  Write-Host 'Local configuration exists. Kept current secrets and accounts.'
  exit 0
}
function New-LocalSecret { return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant() }
$db = New-LocalSecret
$kcDb = New-LocalSecret
$kcAdmin = New-LocalSecret
$redis = New-LocalSecret
$storage = New-LocalSecret
$session = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$lines = @("DB_PASSWORD=$db", "KEYCLOAK_DB_PASSWORD=$kcDb", "KEYCLOAK_ADMIN_PASSWORD=$kcAdmin", "REDIS_PASSWORD=$redis", "STORAGE_PASSWORD=$storage")
New-Item -ItemType Directory -Path (Join-Path $root '.local/keycloak') -Force | Out-Null
$realm = Get-Content -LiteralPath (Join-Path $root 'infra/keycloak/realm-template.json') -Raw | ConvertFrom-Json
$accounts = @()
foreach ($role in @('buyer', 'seller', 'admin')) {
  $password = New-LocalSecret
  $roles = @('BUYER')
  if ($role -eq 'seller') { $roles += 'SELLER' }
  if ($role -eq 'admin') { $roles += 'ADMIN' }
  $realm.users += [PSCustomObject]@{
    username = "$role.local"; enabled = $true; emailVerified = $true
    email = "$role@vshop.local"; firstName = 'VShop'; lastName = "$role local"
    realmRoles = $roles
    credentials = @(@{ type = 'password'; value = $password; temporary = $false })
  }
  $accounts += [PSCustomObject]@{ username = "$role.local"; password = $password; roles = $roles }
}
$realm | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $root '.local/keycloak/vshop-realm.json') -Encoding utf8
$accounts | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root '.local/accounts.json') -Encoding utf8
$webEnv = @('API_BASE_URL=http://localhost:8081', 'KEYCLOAK_ISSUER=http://localhost:8180/realms/vshop', 'WEB_BASE_URL=http://localhost:3100', "SESSION_SECRET=$session", "REDIS_URL=redis://:$redis@localhost:6380")
$webEnv | Set-Content -LiteralPath (Join-Path $root 'apps/web/.env.local') -Encoding utf8
$lines | Set-Content -LiteralPath $envFile -Encoding utf8
Write-Host 'Local configuration generated. Credentials are in .local/accounts.json (ignored by Git).'
