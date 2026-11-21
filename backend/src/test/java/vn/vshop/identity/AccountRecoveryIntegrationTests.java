package vn.vshop.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.vshop.modules.identity.gateway.IdentityProvisioner;
import vn.vshop.modules.identity.gateway.IdentityProvisioner.Outcome;

/** Password reset and e-mail verification only ask the identity provider to send a link. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.payment.payos.client-id=",
            "vshop.payment.payos.api-key=",
            "vshop.payment.payos.checksum-key=",
            "vshop.payment.vnpay.tmn-code=",
            "vshop.payment.vnpay.hash-secret="
        })
@AutoConfigureMockMvc
class AccountRecoveryIntegrationTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean IdentityProvisioner identities;

    @BeforeEach
    void provider() {
        reset(identities);
        when(identities.configured()).thenReturn(true);
        when(identities.sendPasswordReset(any())).thenReturn(true);
        when(identities.sendVerificationEmail(any())).thenReturn(true);
        when(identities.createBuyer(any())).thenReturn(Outcome.CREATED);
    }

    @Test
    void forgotPasswordAnswers202WhateverTheAddressAndLimitsRepeats() throws Exception {
        String email = "reset." + UUID.randomUUID() + "@example.com";
        forgot(email.toUpperCase()).andExpect(status().isAccepted());
        verify(identities).sendPasswordReset(email);
        forgot(email).andExpect(status().isAccepted());
        forgot(email).andExpect(status().isAccepted());
        forgot(email).andExpect(status().isTooManyRequests());
        // A different, unknown address looks exactly the same.
        forgot("nobody." + UUID.randomUUID() + "@example.com").andExpect(status().isAccepted());
    }

    @Test
    void badInputAndProviderTroubleAreReportedPlainly() throws Exception {
        forgot("not-an-email").andExpect(status().isBadRequest());
        verify(identities, never()).sendPasswordReset(any());
        when(identities.sendPasswordReset(any())).thenReturn(false);
        forgot("fail." + UUID.randomUUID() + "@example.com")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNAVAILABLE"));
        when(identities.configured()).thenReturn(false);
        forgot("none." + UUID.randomUUID() + "@example.com")
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void emailVerificationNeedsASignedInPersonAndIsLimited() throws Exception {
        mvc.perform(post("/api/v1/me/email-verification")).andExpect(status().isUnauthorized());
        String subject = UUID.randomUUID().toString();
        verifyAs(subject).andExpect(status().isAccepted());
        verify(identities).sendVerificationEmail(eq(subject));
        verifyAs(subject).andExpect(status().isAccepted());
        verifyAs(subject).andExpect(status().isAccepted());
        verifyAs(subject).andExpect(status().isTooManyRequests());
        when(identities.sendVerificationEmail(any())).thenReturn(false);
        verifyAs(UUID.randomUUID().toString()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void signUpIsLimitedPerAddressEvenWithoutTheWebBff() throws Exception {
        String email = "limit." + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            register(email).andExpect(status().isCreated());
        }
        register(email).andExpect(status().isTooManyRequests());
    }

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(
                post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions verifyAs(String subject) throws Exception {
        return mvc.perform(
                post("/api/v1/me/email-verification")
                        .with(
                                jwt().jwt(j -> j.subject(subject))
                                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))));
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fullName\":\"Hoa Lan\",\"email\":\""
                                        + email
                                        + "\",\"password\":\"matkhau123\",\"acceptTerms\":true}"));
    }
}
