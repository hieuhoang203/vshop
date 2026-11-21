# Applies sign-up / sign-in / e-mail settings to an EXISTING local Keycloak realm (new setups get them
# from init-local.ps1). Idempotent. It
#   - creates the server-only clients "vshop-provisioner" (account creation, e-mail links) and
#     "vshop-web-direct" (the web sign-in form; confidential, secret only in the server environment),
#   - gives the provisioner service account the least rights it needs,
#   - sets the password policy, the VShop login theme, reset-password and the realm SMTP server
#     (Mailpit locally; override with KEYCLOAK_SMTP_* in .env for a real provider),
#   - stores the client secrets in .env (and the web one in apps/web/.env.local) when missing.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$webEnvFile = Join-Path $root 'apps/web/.env.local'

function Read-EnvLines($path) { if (Test-Path -LiteralPath $path) { @(Get-Content -LiteralPath $path) } else { @() } }
function Get-EnvValue($lines, $name) {
  $line = $lines | Where-Object { $_.StartsWith("$name=") } | Select-Object -First 1
  if ($line) { $line.Substring($name.Length + 1) } else { $null }
}
# Appends NAME=value on its own line (the file may not end with a newline) unless already present.
function Add-EnvValue($path, $name, $value) {
  if (Get-EnvValue (Read-EnvLines $path) $name) { return $false }
  $raw = if (Test-Path -LiteralPath $path) { Get-Content -LiteralPath $path -Raw } else { '' }
  $sep = if ($raw -and -not $raw.EndsWith("`n")) { "`n" } else { '' }
  Add-Content -LiteralPath $path -Value "${sep}$name=$value"
  return $true
}

$lines = Read-EnvLines $envFile
$adminPassword = Get-EnvValue $lines 'KEYCLOAK_ADMIN_PASSWORD'
if (-not $adminPassword) { throw 'Run init-local.ps1 first.' }
$token = Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Body @{
  grant_type = 'password'; client_id = 'admin-cli'; username = 'vshop-local-admin'; password = $adminPassword
}
$h = @{ Authorization = "Bearer $($token.access_token)" }
$admin = 'http://localhost:8180/admin/realms/vshop'
$template = Get-Content -LiteralPath (Join-Path $root 'infra/keycloak/realm-template.json') -Raw | ConvertFrom-Json

function Find-Client($clientId) { @(Invoke-RestMethod -Uri "$admin/clients?clientId=$clientId" -Headers $h | Where-Object { $_ })[0] }
function Ensure-Client($clientId) {
  $existing = Find-Client $clientId
  $def = $template.clients | Where-Object clientId -eq $clientId
  if (-not $def) { throw "Template has no client $clientId" }
  $body = $def | ConvertTo-Json -Depth 20
  if ($existing) {
    Invoke-RestMethod -Method Put -Uri "$admin/clients/$($existing.id)" -Headers $h -ContentType 'application/json' -Body $body | Out-Null
  } else {
    Invoke-RestMethod -Method Post -Uri "$admin/clients" -Headers $h -ContentType 'application/json' -Body $body | Out-Null
  }
  Find-Client $clientId
}

# Provisioner: account creation and e-mail links, least rights.
$provisioner = Ensure-Client 'vshop-provisioner'
$provisionerSecret = (Invoke-RestMethod -Uri "$admin/clients/$($provisioner.id)/client-secret" -Headers $h).value
$sa = Invoke-RestMethod -Uri "$admin/clients/$($provisioner.id)/service-account-user" -Headers $h
$rm = (Find-Client 'realm-management').id
$roles = @(@('manage-users', 'view-users', 'view-realm') | ForEach-Object { Invoke-RestMethod -Uri "$admin/clients/$rm/roles/$_" -Headers $h })
Invoke-RestMethod -Method Post -Uri "$admin/users/$($sa.id)/role-mappings/clients/$rm" -Headers $h -ContentType 'application/json' -Body (ConvertTo-Json -InputObject $roles -Depth 5) | Out-Null

# Web sign-in form client (password grant, only usable with its secret held by the web server).
$direct = Ensure-Client 'vshop-web-direct'
$directSecret = (Invoke-RestMethod -Uri "$admin/clients/$($direct.id)/client-secret" -Headers $h).value

# Web client: base URL and the sign-in page as a valid return address of e-mail links.
$web = Find-Client 'vshop-web'
$webDef = $template.clients | Where-Object clientId -eq 'vshop-web'
$web | Add-Member -NotePropertyName baseUrl -NotePropertyValue $webDef.baseUrl -Force
$web | Add-Member -NotePropertyName redirectUris -NotePropertyValue @($webDef.redirectUris) -Force
Invoke-RestMethod -Method Put -Uri "$admin/clients/$($web.id)" -Headers $h -ContentType 'application/json' -Body ($web | ConvertTo-Json -Depth 20) | Out-Null

# Realm: password policy, theme, language, reset password and SMTP.
$realm = Invoke-RestMethod -Uri $admin -Headers $h
foreach ($name in @('passwordPolicy', 'loginTheme', 'internationalizationEnabled', 'supportedLocales', 'defaultLocale', 'displayName', 'resetPasswordAllowed', 'emailTheme')) {
  $realm | Add-Member -NotePropertyName $name -NotePropertyValue $template.$name -Force
}
$smtp = @{}
$template.smtpServer.PSObject.Properties | ForEach-Object { $smtp[$_.Name] = $_.Value }
$map = @{ host = 'KEYCLOAK_SMTP_HOST'; port = 'KEYCLOAK_SMTP_PORT'; from = 'KEYCLOAK_SMTP_FROM'; user = 'KEYCLOAK_SMTP_USER'; password = 'KEYCLOAK_SMTP_PASSWORD'; starttls = 'KEYCLOAK_SMTP_STARTTLS'; ssl = 'KEYCLOAK_SMTP_SSL' }
foreach ($key in $map.Keys) {
  $value = Get-EnvValue $lines $map[$key]
  if ($value) { $smtp[$key] = $value }
}
if ($smtp.ContainsKey('user') -and $smtp['user']) { $smtp['auth'] = 'true' }
$realm | Add-Member -NotePropertyName smtpServer -NotePropertyValue ([PSCustomObject]$smtp) -Force
Invoke-RestMethod -Method Put -Uri $admin -Headers $h -ContentType 'application/json' -Body ($realm | ConvertTo-Json -Depth 30) | Out-Null

if (Add-EnvValue $envFile 'KEYCLOAK_PROVISIONER_SECRET' $provisionerSecret) { Write-Host 'KEYCLOAK_PROVISIONER_SECRET written to .env. Restart the API to pick it up.' }
if (Add-EnvValue $envFile 'KEYCLOAK_WEB_DIRECT_SECRET' $directSecret) { Write-Host 'KEYCLOAK_WEB_DIRECT_SECRET written to .env.' }
if (Add-EnvValue $webEnvFile 'KEYCLOAK_WEB_DIRECT_SECRET' $directSecret) { Write-Host 'KEYCLOAK_WEB_DIRECT_SECRET written to apps/web/.env.local. Restart the web app.' }
Write-Host 'Sign-up, sign-in form, e-mail and password policy configured. Existing users preserved.'
