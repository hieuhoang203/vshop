package vn.vshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.platform.gateway.EmailSender;
import vn.vshop.modules.platform.service.EmailDeliveryService;
import vn.vshop.modules.platform.service.NotificationService;

/** Opt-in email copies of notifications: consent, queueing, retry and the send-time recheck. */
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
@Transactional
class EmailNotificationIntegrationTests {
    private static final String ADDRESS = "mailer.one@example.com";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired NotificationService notifications;
    @Autowired EmailDeliveryService emails;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EmailSender sender;

    @BeforeEach
    void mailServer() {
        reset(sender);
        when(sender.configured()).thenReturn(true);
    }

    @Test
    void emailGoesOnlyToThoseWhoOptedInAndIsRecheckedAtSendTime() throws Exception {
        // Nothing is queued before opting in.
        UUID account = account("mail-1");
        notifications.notify(account, "T", "Đơn mới", "Bạn có đơn mới", "/orders", "k1");
        assertThat(count("1=1")).isZero();

        // Opting in uses the verified claim; the address is stored encrypted and shown masked.
        mvc.perform(
                        put("/api/v1/me/email-notifications")
                                .with(user("mail-1", ADDRESS, true))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.maskedEmail").value("m***@example.com"))
                .andExpect(jsonPath("$.available").value(true));
        assertThat(
                        jdbc.sql(
                                        "SELECT email_ciphertext FROM identity.notification_email WHERE account_id = :a")
                                .param("a", account)
                                .query(String.class)
                                .single())
                .doesNotContain("mailer");

        notifications.notify(account, "T", "Đơn mới", "Bạn có đơn mới", "/orders", "k2");
        notifications.notify(account, "T", "Đơn mới", "Bạn có đơn mới", "/orders", "k2");
        assertThat(count("status = 'PENDING'")).isEqualTo(1);

        assertThat(emails.sendDue(10)).isEqualTo(1);
        ArgumentCaptor<EmailSender.Message> sent =
                ArgumentCaptor.forClass(EmailSender.Message.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo(ADDRESS);
        assertThat(sent.getValue().subject()).isEqualTo("[VShop] Đơn mới");
        assertThat(sent.getValue().text()).contains("/orders").contains("/notifications/settings");
        assertThat(count("status = 'SENT'")).isEqualTo(1);
        assertThat(emails.sendDue(10)).isZero();

        // Mail waiting when the person turns email off is skipped, never sent.
        notifications.notify(account, "T", "Đơn nữa", "Nội dung", "/orders", "k3");
        mvc.perform(
                        put("/api/v1/me/email-notifications")
                                .with(user("mail-1", ADDRESS, true))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":false}"))
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(emails.sendDue(10)).isZero();
        assertThat(count("status = 'SKIPPED'")).isEqualTo(1);
        notifications.notify(account, "T", "Sau khi tắt", "Nội dung", "/orders", "k4");
        assertThat(count("status = 'PENDING'")).isZero();
        verify(sender, org.mockito.Mockito.times(1)).send(any());
    }

    @Test
    void aFailingMailServerIsRetriedWithDelayAndEventuallyGivenUp() throws Exception {
        UUID account = account("mail-2");
        mvc.perform(
                        put("/api/v1/me/email-notifications")
                                .with(user("mail-2", ADDRESS, true))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        notifications.notify(account, "T", "Tin", "Nội dung", null, "r1");
        doThrow(new IllegalStateException("smtp down")).when(sender).send(any());

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(emails.sendDue(10)).isZero();
            assertThat(
                            count(
                                    "status = 'PENDING' AND attempts = "
                                            + attempt
                                            + " AND next_attempt_at > now() AND last_error LIKE '%smtp down%'"))
                    .isEqualTo(1);
            // Not due yet: nothing is tried again before the delay passes.
            assertThat(emails.sendDue(10)).isZero();
            jdbc.sql(
                            "UPDATE platform.email_delivery SET next_attempt_at = now() - interval '1 second'")
                    .update();
        }
        assertThat(emails.sendDue(10)).isZero();
        assertThat(count("status = 'FAILED' AND attempts = 5")).isEqualTo(1);
        jdbc.sql("UPDATE platform.email_delivery SET next_attempt_at = now() - interval '1 hour'")
                .update();
        assertThat(emails.sendDue(10)).isZero();
        // The notification itself is untouched by the failed mail.
        mvc.perform(
                        get("/api/v1/me/notifications/unread-count")
                                .with(user("mail-2", ADDRESS, true)))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void nothingIsQueuedWhenNoMailServerIsConfigured() throws Exception {
        UUID account = account("mail-3");
        mvc.perform(
                        put("/api/v1/me/email-notifications")
                                .with(user("mail-3", ADDRESS, true))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        when(sender.configured()).thenReturn(false);
        notifications.notify(account, "T", "Tin", "Nội dung", null, "n1");
        assertThat(count("1=1")).isZero();
        mvc.perform(get("/api/v1/me/email-notifications").with(user("mail-3", ADDRESS, true)))
                .andExpect(jsonPath("$.available").value(false));
        assertThat(emails.sendDue(10)).isZero();
        verify(sender, never()).send(any());
    }

    @Test
    void unverifiedOrMissingAddressesAreRefused() throws Exception {
        mvc.perform(
                        put("/api/v1/me/email-notifications")
                                .with(user("mail-4", ADDRESS, false))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        put("/api/v1/me/email-notifications")
                                .with(user("mail-4", null, true))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/me/email-notifications")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    /** Creates the account by reading the status, the way any first request would. */
    private UUID account(String subject) throws Exception {
        mvc.perform(get("/api/v1/me/email-notifications").with(user(subject, ADDRESS, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        return jdbc.sql("SELECT id FROM identity.account WHERE external_subject = :s")
                .param("s", subject)
                .query(UUID.class)
                .single();
    }

    private long count(String where) {
        return jdbc.sql("SELECT count(*) FROM platform.email_delivery WHERE " + where)
                .query(Long.class)
                .single();
    }

    private static JwtRequestPostProcessor user(String subject, String email, boolean verified) {
        return jwt().jwt(
                        j -> {
                            j.subject(subject).claim("email_verified", verified);
                            if (email != null) {
                                j.claim("email", email);
                            }
                        })
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }
}
