param(
    [Parameter(Mandatory = $true)][string]$Username,
    [string]$KeycloakUrl = 'http://localhost:8180',
    [string]$Realm = 'vshop',
    [string]$AdminUser = 'vshop-local-admin',
    [string]$AdminPassword
)
# Removes the authenticator (OTP) credentials of one user, so they must enrol again at next login.
# Use it when an admin loses their device, or when the e2e tests no longer know the local secret
# (.local/totp.json). It does not touch the password or any other credential.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $AdminPassword) {
    $line = Get-Content -LiteralPath (Join-Path $root '.env') | Where-Object { $_.StartsWith('KEYCLOAK_ADMIN_PASSWORD=') } | Select-Object -First 1
    if (-not $line) { throw 'Run init-local.ps1 first, or pass -AdminPassword.' }
    $AdminPassword = $line.Substring('KEYCLOAK_ADMIN_PASSWORD='.Length)
}
$token = Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" -Body @{
    grant_type = 'password'; client_id = 'admin-cli'; username = $AdminUser; password = $AdminPassword
}
$headers = @{ Authorization = "Bearer $($token.access_token)" }
$base = "$KeycloakUrl/admin/realms/$Realm"
$user = Invoke-RestMethod -Uri "$base/users?username=$([uri]::EscapeDataString($Username))&exact=true" -Headers $headers | Select-Object -First 1
if (-not $user) { throw "User '$Username' not found." }
$removed = 0
foreach ($credential in (Invoke-RestMethod -Uri "$base/users/$($user.id)/credentials" -Headers $headers)) {
    if ($credential.type -eq 'otp') {
        Invoke-RestMethod -Method Delete -Uri "$base/users/$($user.id)/credentials/$($credential.id)" -Headers $headers | Out-Null
        $removed++
    }
}
$stateFile = Join-Path $root '.local/totp.json'
if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json -AsHashtable
    if ($state.ContainsKey($Username)) {
        $state.Remove($Username)
        $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $stateFile -Encoding utf8
    }
}
Write-Host "Removed $removed authenticator credential(s) for $Username. They will be asked to enrol at next login."
