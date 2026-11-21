package vn.vshop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.vshop.modules.identity.gateway.IdentityProvisioner;
import vn.vshop.modules.identity.gateway.IdentityProvisioner.NewIdentity;
import vn.vshop.modules.identity.gateway.IdentityProvisioner.Outcome;

/** Self-service sign-up: every rule is enforced by the API, whatever the form did. */
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
class RegistrationIntegrationTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean IdentityProvisioner identities;

    @BeforeEach
    void provider() {
        reset(identities);
        when(identities.configured()).thenReturn(true);
        when(identities.createBuyer(any())).thenReturn(Outcome.CREATED);
    }

    @Test
    void aValidFormCreatesABuyerWithoutSigningIn() throws Exception {
        register("  Nguyễn   Thị Hoa ", " Hoa.Nguyen@Example.com ", "matkhau123", true)
                .andExpect(status().isCreated());
        ArgumentCaptor<NewIdentity> sent = ArgumentCaptor.forClass(NewIdentity.class);
        verify(identities).createBuyer(sent.capture());
        // Lower-case address, tidy spaces, Vietnamese order: given name last.
        assertThat(sent.getValue().email()).isEqualTo("hoa.nguyen@example.com");
        assertThat(sent.getValue().givenName()).isEqualTo("Nguyễn Thị");
        assertThat(sent.getValue().familyName()).isEqualTo("Hoa");
        assertThat(sent.getValue().password()).isEqualTo("matkhau123");
    }

    @Test
    void namesTypedWithCombiningMarksAreAccepted() throws Exception {
        // "Nguyen Van A" with the accent on e as a separate combining mark (NFD).
        register("Nguyễn Văn An", "an@example.com", "matkhau123", true)
                .andExpect(status().isCreated());
        ArgumentCaptor<NewIdentity> sent = ArgumentCaptor.forClass(NewIdentity.class);
        verify(identities).createBuyer(sent.capture());
        assertThat(sent.getValue().givenName()).isEqualTo("Nguyễn Văn");
    }

    @Test
    void invalidFormsAreRefusedBeforeTheIdentityProviderIsCalled() throws Exception {
        register("H", "hoa@example.com", "matkhau123", true).andExpect(status().isBadRequest());
        register("Hoa 123", "hoa@example.com", "matkhau123", true)
                .andExpect(status().isBadRequest());
        register("Hoa Lan", "not-an-email", "matkhau123", true).andExpect(status().isBadRequest());
        register("Hoa Lan", "hoa@example.com", "short1", true).andExpect(status().isBadRequest());
        register("Hoa Lan", "hoa@example.com", "onlyletters", true)
                .andExpect(status().isBadRequest());
        register("Hoa Lan", "hoa@example.com", "123456789", true)
                .andExpect(status().isBadRequest());
        register("Hoa Lan", "hoa@example.com", " matkhau123 ", true)
                .andExpect(status().isBadRequest());
        register("Hoa Lan", "hoa1234@example.com", "hoa1234@example.com", true)
                .andExpect(status().isBadRequest());
        register("Hoa Lan", "hoa@example.com", "a".repeat(30) + "1".repeat(40), true)
                .andExpect(status().isBadRequest());
        register("Hoa Lan", "hoa@example.com", "matkhau123", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_INPUT"));
        mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"hoa@example.com\"}"))
                .andExpect(status().isBadRequest());
        verify(identities, never()).createBuyer(any());
    }

    @Test
    void anExistingEmailIsReportedAsAConflictWithoutEchoingThePassword() throws Exception {
        when(identities.createBuyer(any())).thenReturn(Outcome.EMAIL_TAKEN);
        String body =
                register("Hoa Lan", "hoa@example.com", "matkhau123", true)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain("matkhau123");
    }

    @Test
    void aPasswordTheProviderRejectsIsAFixableInputError() throws Exception {
        when(identities.createBuyer(any())).thenReturn(Outcome.PASSWORD_REJECTED);
        register("Hoa Lan", "hoa@example.com", "matkhau123", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_INPUT"));
    }

    @Test
    void whenTheIdentityProviderIsMissingOrDownTheAnswerIs503() throws Exception {
        when(identities.createBuyer(any())).thenReturn(Outcome.UNAVAILABLE);
        register("Hoa Lan", "hoa@example.com", "matkhau123", true)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REGISTRATION_UNAVAILABLE"));
        when(identities.configured()).thenReturn(false);
        register("Hoa Lan", "hoa@example.com", "matkhau123", true)
                .andExpect(status().isServiceUnavailable());
    }

    private ResultActions register(String name, String email, String password, boolean terms)
            throws Exception {
        String json =
                "{\"fullName\":\""
                        + name
                        + "\",\"email\":\""
                        + email
                        + "\",\"password\":\""
                        + password
                        + "\",\"acceptTerms\":"
                        + terms
                        + "}";
        return mvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }
}
