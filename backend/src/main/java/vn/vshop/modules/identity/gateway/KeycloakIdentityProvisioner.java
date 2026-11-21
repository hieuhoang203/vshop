package vn.vshop.modules.identity.gateway;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Keycloak Admin REST API with a dedicated confidential client ({@code vshop-provisioner}) whose
 * service account may only manage users and read roles. The secret comes from the environment. A
 * user whose BUYER role cannot be assigned is deleted again, so no half-created identity remains.
 */
@Component
public class KeycloakIdentityProvisioner implements IdentityProvisioner {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakIdentityProvisioner.class);
    private static final String BUYER = "BUYER";

    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper json;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    KeycloakIdentityProvisioner(
            @Value("${vshop.identity.keycloak.base-url:http://localhost:8180}") String baseUrl,
            @Value("${vshop.identity.keycloak.realm:vshop}") String realm,
            @Value("${vshop.identity.keycloak.client-id:vshop-provisioner}") String clientId,
            @Value("${vshop.identity.keycloak.client-secret:}") String clientSecret,
            ObjectMapper json) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.json = json;
    }

    @Override
    public boolean configured() {
        return !clientSecret.isBlank();
    }

    @Override
    public Outcome createBuyer(NewIdentity identity) {
        try {
            String token = adminToken();
            if (token == null) {
                return Outcome.UNAVAILABLE;
            }
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", identity.email());
            user.put("email", identity.email());
            user.put("firstName", identity.givenName());
            user.put("lastName", identity.familyName());
            user.put("enabled", true);
            // Not verified until the person proves the address (done with a mail server later).
            user.put("emailVerified", false);
            user.put(
                    "credentials",
                    List.of(
                            Map.of(
                                    "type",
                                    "password",
                                    "value",
                                    identity.password(),
                                    "temporary",
                                    false)));
            HttpResponse<String> created =
                    send("POST", "/admin/realms/" + realm + "/users", token, user);
            if (created.statusCode() == 409) {
                return Outcome.EMAIL_TAKEN;
            }
            if (created.statusCode() == 400) {
                if (created.body().contains("invalidPassword")) {
                    return Outcome.PASSWORD_REJECTED;
                }
                // Any other 400 is a mistake in our request, not something the person can fix.
                LOG.warn("Keycloak rejected the new user as invalid: {}", created.body());
                return Outcome.UNAVAILABLE;
            }
            String location = created.headers().firstValue("Location").orElse("");
            if (created.statusCode() != 201 || location.isBlank()) {
                LOG.warn("Keycloak refused user creation: HTTP {}", created.statusCode());
                return Outcome.UNAVAILABLE;
            }
            String userId = location.substring(location.lastIndexOf('/') + 1);
            if (!assignBuyer(token, userId)) {
                send("DELETE", "/admin/realms/" + realm + "/users/" + userId, token, null);
                return Outcome.UNAVAILABLE;
            }
            return Outcome.CREATED;
        } catch (java.io.IOException | RuntimeException e) {
            LOG.warn("Keycloak provisioning failed: {}", e.toString());
            return Outcome.UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.UNAVAILABLE;
        }
    }

    private boolean assignBuyer(String token, String userId)
            throws java.io.IOException, InterruptedException {
        HttpResponse<String> role =
                send("GET", "/admin/realms/" + realm + "/roles/" + BUYER, token, null);
        if (role.statusCode() != 200) {
            LOG.warn("Keycloak role {} unreadable: HTTP {}", BUYER, role.statusCode());
            return false;
        }
        JsonNode node = json.readTree(role.body());
        HttpResponse<String> mapped =
                send(
                        "POST",
                        "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                        token,
                        List.of(Map.of("id", node.path("id").asText(), "name", BUYER)));
        return mapped.statusCode() / 100 == 2;
    }

    private String adminToken() throws java.io.IOException, InterruptedException {
        String form =
                "grant_type=client_credentials&client_id="
                        + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&client_secret="
                        + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        HttpResponse<String> response =
                http.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                baseUrl
                                                        + "/realms/"
                                                        + realm
                                                        + "/protocol/openid-connect/token"))
                                .timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(form))
                                .build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            LOG.warn("Keycloak token request failed: HTTP {}", response.statusCode());
            return null;
        }
        return json.readTree(response.body()).path("access_token").asText(null);
    }

    private HttpResponse<String> send(String method, String path, String token, Object body)
            throws java.io.IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(
                            method,
                            HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        return http.send(
                request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
