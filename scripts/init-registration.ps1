# Applies self-service sign-up settings to an EXISTING local Keycloak realm (new setups get them from
# init-local.ps1). Idempotent: creates the server-only client "vshop-provisioner", gives its service
# account the least rights it needs (manage/view users, view realm), sets the password policy and
# stores the client secret in .env as KEYCLOAK_PROVISIONER_SECRET when it is not there yet.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$lines = @(Get-Content -LiteralPath $envFile)
$adminLine = $lines | Where-Object { $_.StartsWith('KEYCLOAK_ADMIN_PASSWORD=') } | Select-Object -First 1
if (-not $adminLine) { throw 'Run init-local.ps1 first.' }
$token = Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Body @{
  grant_type = 'password'; client_id = 'admin-cli'; username = 'vshop-local-admin'; password = $adminLine.Substring('KEYCLOAK_ADMIN_PASSWORD='.Length)
}
$h = @{ Authorization = "Bearer $($token.access_token)" }
$admin = 'http://localhost:8180/admin/realms/vshop'
$template = Get-Content -LiteralPath (Join-Path $root 'infra/keycloak/realm-template.json') -Raw | ConvertFrom-Json
$def = $template.clients | Where-Object clientId -eq 'vshop-provisioner'
$found = @(Invoke-RestMethod -Uri "$admin/clients?clientId=vshop-provisioner" -Headers $h | Where-Object { $_ })
if ($found.Count -eq 0) {
  Invoke-RestMethod -Method Post -Uri "$admin/clients" -Headers $h -ContentType 'application/json' -Body ($def | ConvertTo-Json -Depth 10) | Out-Null
  $found = @(Invoke-RestMethod -Uri "$admin/clients?clientId=vshop-provisioner" -Headers $h | Where-Object { $_ })
}
$id = $found[0].id
$secret = (Invoke-RestMethod -Uri "$admin/clients/$id/client-secret" -Headers $h).value
$sa = Invoke-RestMethod -Uri "$admin/clients/$id/service-account-user" -Headers $h
$web = @(Invoke-RestMethod -Uri "$admin/clients?clientId=vshop-web" -Headers $h)[0]
$web | Add-Member -NotePropertyName baseUrl -NotePropertyValue ($template.clients | Where-Object clientId -eq 'vshop-web').baseUrl -Force
Invoke-RestMethod -Method Put -Uri "$admin/clients/$($web.id)" -Headers $h -ContentType 'application/json' -Body ($web | ConvertTo-Json -Depth 20) | Out-Null
$rm = @(Invoke-RestMethod -Uri "$admin/clients?clientId=realm-management" -Headers $h)[0].id
$wanted = @('manage-users', 'view-users', 'view-realm')
$roles = @($wanted | ForEach-Object { Invoke-RestMethod -Uri "$admin/clients/$rm/roles/$_" -Headers $h })
Invoke-RestMethod -Method Post -Uri "$admin/users/$($sa.id)/role-mappings/clients/$rm" -Headers $h -ContentType 'application/json' -Body (ConvertTo-Json -InputObject $roles -Depth 5) | Out-Null
$realm = Invoke-RestMethod -Uri $admin -Headers $h
$realm | Add-Member -NotePropertyName passwordPolicy -NotePropertyValue $template.passwordPolicy -Force
# VShop-styled Vietnamese sign-in page (theme is mounted from infra/keycloak/themes).
foreach ($name in @('loginTheme', 'internationalizationEnabled', 'supportedLocales', 'defaultLocale', 'displayName')) {
  $realm | Add-Member -NotePropertyName $name -NotePropertyValue $template.$name -Force
}
Invoke-RestMethod -Method Put -Uri $admin -Headers $h -ContentType 'application/json' -Body ($realm | ConvertTo-Json -Depth 30) | Out-Null
if (-not ($lines | Where-Object { $_.StartsWith('KEYCLOAK_PROVISIONER_SECRET=') })) {
  $raw = Get-Content -LiteralPath $envFile -Raw
  $sep = if ($raw.EndsWith("`n")) { "" } else { "`n" }
  Add-Content -LiteralPath $envFile -Value "${sep}KEYCLOAK_PROVISIONER_SECRET=$secret" -NoNewline:$false
  Write-Host 'KEYCLOAK_PROVISIONER_SECRET written to .env. Restart the API to pick it up.'
}
Write-Host 'Sign-up client and password policy configured. Existing users and clients preserved.'
