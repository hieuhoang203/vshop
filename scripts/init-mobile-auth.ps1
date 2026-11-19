$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$secretLine = Get-Content -LiteralPath (Join-Path $root '.env') | Where-Object { $_.StartsWith('KEYCLOAK_ADMIN_PASSWORD=') } | Select-Object -First 1
if (-not $secretLine) { throw 'Run init-local.ps1 first.' }
$password = $secretLine.Substring('KEYCLOAK_ADMIN_PASSWORD='.Length)
$token = Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Body @{
    grant_type = 'password'; client_id = 'admin-cli'; username = 'vshop-local-admin'; password = $password
}
$headers = @{ Authorization = "Bearer $($token.access_token)" }
$realm = Get-Content -LiteralPath (Join-Path $root 'infra/keycloak/realm-template.json') -Raw | ConvertFrom-Json
$client = $realm.clients | Where-Object clientId -eq 'vshop-mobile'
if (-not $client) { throw 'Mobile client definition is missing.' }
$url = 'http://localhost:8180/admin/realms/vshop/clients'
$existing = Invoke-RestMethod -Uri "${url}?clientId=vshop-mobile" -Headers $headers
$body = $client | ConvertTo-Json -Depth 20
if ($existing -and $existing[0].id) {
    Invoke-RestMethod -Method Put -Uri "$url/$($existing[0].id)" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
} else {
    Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
}
Write-Host 'Local vshop-mobile public client configured with exact callback, PKCE S256 and API audience. Existing users and web client preserved.'
