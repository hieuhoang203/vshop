param(
    [string]$KeycloakUrl = 'http://localhost:8180',
    [string]$Realm = 'vshop',
    [string]$AdminUser = 'vshop-local-admin',
    # Defaults to KEYCLOAK_ADMIN_PASSWORD from .env. Never printed.
    [string]$AdminPassword
)
# Applies the admin second-factor setup to a Keycloak realm that already exists. New realms get the
# same configuration from infra/keycloak/realm-template.json; keep the two in step.
#
#  * Browser login flow "browser-admin-mfa": cookie OR (username/password, then TOTP for ADMIN users).
#    An ADMIN without an authenticator is forced to enrol one at first login.
#  * "amr" claim (pwd, otp) on the vshop-web and vshop-mobile access tokens. The API requires amr=otp
#    for /api/v1/admin/**.
#  * SSO idle 30 min and max 8 h; refresh tokens are not single-use (the web BFF refreshes them).
#  * Existing sessions are logged out, because a still-valid SSO cookie would otherwise skip the OTP.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $AdminPassword) {
    $line = Get-Content -LiteralPath (Join-Path $root '.env') | Where-Object { $_.StartsWith('KEYCLOAK_ADMIN_PASSWORD=') } | Select-Object -First 1
    if (-not $line) { throw 'Run init-local.ps1 first, or pass -AdminPassword.' }
    $AdminPassword = $line.Substring('KEYCLOAK_ADMIN_PASSWORD='.Length)
}
# Master-realm admin tokens live only ~60 s, so renew them as needed.
function Get-AdminHeaders {
    if (-not $script:headers -or ((Get-Date) - $script:tokenAt).TotalSeconds -gt 30) {
        $token = Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" -Body @{
            grant_type = 'password'; client_id = 'admin-cli'; username = $AdminUser; password = $AdminPassword
        }
        $script:headers = @{ Authorization = "Bearer $($token.access_token)" }
        $script:tokenAt = Get-Date
    }
    $script:headers
}
$base = "$KeycloakUrl/admin/realms/$Realm"
function Call([string]$Method, [string]$Path, $Body = $null) {
    $args = @{ Method = $Method; Uri = "$base$Path"; Headers = (Get-AdminHeaders) }
    if ($null -ne $Body) { $args.ContentType = 'application/json'; $args.Body = ($Body | ConvertTo-Json -Depth 20) }
    try { $result = Invoke-RestMethod @args } catch { Write-Host "Keycloak call failed: $Method $Path"; throw }
    # PowerShell 7 returns a JSON array as ONE object; emit the items so Where-Object filters them.
    if ($result -is [System.Array]) { foreach ($item in $result) { $item } } else { $result }
}
function Enc([string]$Alias) { [uri]::EscapeDataString($Alias) }

$top = 'browser-admin-mfa'
$forms = 'browser-admin-mfa forms'
$otpFlow = 'browser-admin-mfa admin otp'

$existing = Call GET '/authentication/flows' | Where-Object { $_.alias -eq $top }
if (-not $existing) {
  try {
    Call POST '/authentication/flows' @{ alias = $top; description = 'Browser login; ADMIN users must also pass TOTP'; providerId = 'basic-flow'; topLevel = $true; builtIn = $false } | Out-Null
    Call POST "/authentication/flows/$(Enc $top)/executions/execution" @{ provider = 'auth-cookie' } | Out-Null
    Call POST "/authentication/flows/$(Enc $top)/executions/flow" @{ alias = $forms; type = 'basic-flow'; provider = 'registration-page-form'; description = 'Password then conditional OTP' } | Out-Null
    Call POST "/authentication/flows/$(Enc $forms)/executions/execution" @{ provider = 'auth-username-password-form' } | Out-Null
    Call POST "/authentication/flows/$(Enc $forms)/executions/flow" @{ alias = $otpFlow; type = 'basic-flow'; provider = 'registration-page-form'; description = 'OTP is required when the user has the ADMIN role' } | Out-Null
    Call POST "/authentication/flows/$(Enc $otpFlow)/executions/execution" @{ provider = 'conditional-user-role' } | Out-Null
    Call POST "/authentication/flows/$(Enc $otpFlow)/executions/execution" @{ provider = 'auth-otp-form' } | Out-Null

    function Set-Requirement([string]$Flow, [scriptblock]$Match, [string]$Requirement) {
        $execution = Call GET "/authentication/flows/$(Enc $Flow)/executions" | Where-Object $Match | Select-Object -First 1
        if (-not $execution) { throw "Execution not found in $Flow" }
        $execution | Add-Member -NotePropertyName requirement -NotePropertyValue $Requirement -Force
        Call PUT "/authentication/flows/$(Enc $Flow)/executions" $execution | Out-Null
    }
    Set-Requirement $top { $_.providerId -eq 'auth-cookie' } 'ALTERNATIVE'
    Set-Requirement $top { $_.displayName -eq $forms } 'ALTERNATIVE'
    Set-Requirement $forms { $_.providerId -eq 'auth-username-password-form' } 'REQUIRED'
    Set-Requirement $forms { $_.displayName -eq $otpFlow } 'CONDITIONAL'
    Set-Requirement $otpFlow { $_.providerId -eq 'conditional-user-role' } 'REQUIRED'
    Set-Requirement $otpFlow { $_.providerId -eq 'auth-otp-form' } 'REQUIRED'

    function Add-Config([string]$Flow, [string]$Provider, [string]$Alias, $Config) {
        $execution = Call GET "/authentication/flows/$(Enc $Flow)/executions" | Where-Object { $_.providerId -eq $Provider } | Select-Object -First 1
        Call POST "/authentication/executions/$($execution.id)/config" @{ alias = $Alias; config = $Config } | Out-Null
    }
    Add-Config $otpFlow 'conditional-user-role' 'vshop-admin-role' @{ condUserRole = 'ADMIN'; negate = 'false' }
    Add-Config $otpFlow 'auth-otp-form' 'vshop-otp-amr' @{ 'default.reference.value' = 'otp'; 'default.reference.maxAge' = '36000' }
    Add-Config $forms 'auth-username-password-form' 'vshop-pwd-amr' @{ 'default.reference.value' = 'pwd'; 'default.reference.maxAge' = '36000' }
    Write-Host "Created authentication flow '$top'."
  } catch {
    # Never leave a half-built flow behind: a later run would mistake it for a complete one.
    $created = Call GET '/authentication/flows' | Where-Object { $_.alias -eq $top }
    if ($created) { Call DELETE "/authentication/flows/$($created.id)" | Out-Null }
    throw
  }
} else {
    Write-Host "Authentication flow '$top' already exists; leaving its definition unchanged."
}

$realmRep = Call GET ''
$realmRep.browserFlow = $top
$realmRep.ssoSessionIdleTimeout = 1800
$realmRep.ssoSessionMaxLifespan = 28800
$realmRep.revokeRefreshToken = $false
Call PUT '' $realmRep | Out-Null

foreach ($clientId in 'vshop-web', 'vshop-mobile') {
    $client = Call GET "/clients?clientId=$clientId" | Select-Object -First 1
    if (-not $client) { Write-Host "Client $clientId not found; skipped."; continue }
    $mappers = Call GET "/clients/$($client.id)/protocol-mappers/models"
    if (-not ($mappers | Where-Object { $_.name -eq 'vshop-amr' })) {
        Call POST "/clients/$($client.id)/protocol-mappers/models" @{
            name = 'vshop-amr'; protocol = 'openid-connect'; protocolMapper = 'oidc-amr-mapper'
            config = @{ 'access.token.claim' = 'true'; 'id.token.claim' = 'false'; 'introspection.token.claim' = 'true'; 'claim.name' = 'amr' }
        } | Out-Null
        Write-Host "Added amr mapper to $clientId."
    }
}

Call POST '/logout-all' | Out-Null
Write-Host 'Admin MFA configured. All existing sessions were logged out; admins enrol an authenticator at next login.'
Write-Host 'The first session after enrolment has no amr=otp, so log out and sign in again once to use the admin area.'
