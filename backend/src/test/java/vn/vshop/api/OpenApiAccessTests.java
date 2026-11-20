package vn.vshop.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The access rules published in the contract ({@code x-vshop-access}) must match what the security
 * configuration really enforces. For every operation in the generated document this calls the real
 * filter chain: anonymous callers, callers with a role the contract does not list, and ADMIN
 * callers without a second factor. Security is evaluated before any controller code runs, so no
 * business data is touched.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "springdoc.api-docs.enabled=true"
        })
@AutoConfigureMockMvc
class OpenApiAccessTests {
    private static final List<String> ALL_ROLES = List.of("ADMIN", "BUYER", "SELLER");

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void publishedAccessRulesMatchTheEnforcedOnes() throws Exception {
        JsonNode spec =
                new ObjectMapper()
                        .readTree(
                                mvc.perform(get("/v3/api-docs"))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString(StandardCharsets.UTF_8));
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (var path : spec.get("paths").propertyNames()) {
            JsonNode item = spec.get("paths").get(path);
            for (String method : item.propertyNames()) {
                JsonNode access = item.get(method).get("x-vshop-access");
                String label = method.toUpperCase() + " " + path;
                if (access == null) {
                    problems.add(label + " has no x-vshop-access");
                    continue;
                }
                checked++;
                int anonymous = status(method, path, null);
                if (access.get("public").asBoolean()) {
                    if (anonymous == 401 || anonymous == 403) {
                        problems.add(
                                label + " is published as public but anonymous got " + anonymous);
                    }
                    continue;
                }
                if (anonymous != 401) {
                    problems.add(
                            label + " must reject anonymous callers with 401, got " + anonymous);
                }
                List<String> allowed = new ArrayList<>();
                access.get("anyOfRoles").forEach(role -> allowed.add(role.asText()));
                for (String role : ALL_ROLES) {
                    if (!allowed.contains(role)) {
                        int forbidden = status(method, path, token(role, true));
                        if (forbidden != 403) {
                            problems.add(
                                    label
                                            + " must refuse role "
                                            + role
                                            + " with 403, got "
                                            + forbidden);
                        }
                    }
                }
                if (access.path("requiresSecondFactor").asBoolean(false)) {
                    int noOtp = status(method, path, token("ADMIN", false));
                    if (noOtp != 403) {
                        problems.add(
                                label + " must refuse an ADMIN without OTP with 403, got " + noOtp);
                    }
                } else {
                    // Not published as needing OTP: an admin-only mistake would show up above.
                    assertThat(access.has("requiresSecondFactor")).isFalse();
                }
            }
        }
        assertThat(checked).as("operations checked").isGreaterThan(30);
        assertThat(problems).isEmpty();
    }

    private int status(String method, String path, Object token) throws Exception {
        String url =
                path.replaceAll("\\{slug}", "x")
                        .replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        MockHttpServletRequestBuilder builder =
                request(HttpMethod.valueOf(method.toUpperCase()), url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Idempotency-Key", "contract-check-key");
        if (token != null) {
            builder.with((org.springframework.test.web.servlet.request.RequestPostProcessor) token);
        }
        return mvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private static Object token(String role, boolean withOtp) {
        return jwt().jwt(
                        j -> {
                            j.subject("contract-check");
                            if (withOtp) {
                                j.claim("amr", List.of("pwd", "otp"));
                            }
                        })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
