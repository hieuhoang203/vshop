package vn.vshop.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Default configuration: admin APIs demand a role AND proof of a second factor (amr=otp). */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false"
        })
@AutoConfigureMockMvc
@Transactional
class AdminMfaEnforcementTests {
    private static final List<String> ADMIN_GETS =
            List.of(
                    "/api/v1/admin/shops",
                    "/api/v1/admin/analytics/shop-activity?days=7",
                    "/api/v1/admin/products/review-queue");

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void adminWithoutOtpIsRefusedOnEveryAdminEndpointWithAClearCode() throws Exception {
        for (String path : ADMIN_GETS) {
            mvc.perform(get(path).with(admin(null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
            mvc.perform(get(path).with(admin(List.of("pwd"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
        }
        mvc.perform(
                        post("/api/v1/admin/shops/" + UUID.randomUUID() + "/review")
                                .with(admin(List.of("pwd")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"decision\":\"APPROVED\",\"reason\":\"Should not run\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
        mvc.perform(
                        post("/api/v1/admin/products/" + UUID.randomUUID() + "/review")
                                .with(admin(List.of("pwd")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"decision\":\"APPROVED\",\"reason\":\"Should not run\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
    }

    @Test
    void adminWithOtpIsAllowed() throws Exception {
        for (String path : ADMIN_GETS) {
            mvc.perform(get(path).with(admin(List.of("pwd", "otp")))).andExpect(status().isOk());
        }
    }

    @Test
    void otpAloneDoesNotGrantAdminAndAnonymousStaysUnauthorized() throws Exception {
        for (String path : ADMIN_GETS) {
            // A buyer with a second factor is still not an admin, and gets no MFA hint.
            mvc.perform(
                            get(path)
                                    .with(
                                            jwt().jwt(
                                                            j ->
                                                                    j.subject("buyer")
                                                                            .claim(
                                                                                    "amr",
                                                                                    List.of(
                                                                                            "pwd",
                                                                                            "otp")))
                                                    .authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_BUYER"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").doesNotExist());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void nonAdminEndpointsDoNotRequireOtp() throws Exception {
        mvc.perform(get("/api/v1/catalog/products")).andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/seller/shops")
                                .with(
                                        jwt().jwt(j -> j.subject("seller-no-otp"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isOk());
    }

    private static JwtRequestPostProcessor admin(List<String> amr) {
        return jwt().jwt(
                        j -> {
                            j.subject("mfa-admin");
                            if (amr != null) {
                                j.claim("amr", amr);
                            }
                        })
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
