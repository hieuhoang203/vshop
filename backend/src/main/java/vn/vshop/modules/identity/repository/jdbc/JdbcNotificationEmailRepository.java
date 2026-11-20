package vn.vshop.modules.identity.repository.jdbc;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.identity.repository.NotificationEmailRepository;

@Repository
public class JdbcNotificationEmailRepository implements NotificationEmailRepository {
    private final JdbcClient jdbc;

    public JdbcNotificationEmailRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Row> find(UUID accountId) {
        return jdbc.sql(
                        "SELECT account_id, email_ciphertext, enabled, verified_at"
                                + " FROM identity.notification_email WHERE account_id = :a")
                .param("a", accountId)
                .query(Row.class)
                .optional();
    }

    @Override
    public void enable(UUID accountId, String emailCiphertext) {
        jdbc.sql(
                        "INSERT INTO identity.notification_email (account_id, email_ciphertext,"
                                + " enabled, verified_at) VALUES (:a, :c, TRUE, now())"
                                + " ON CONFLICT (account_id) DO UPDATE SET email_ciphertext = :c,"
                                + " enabled = TRUE, verified_at = now(), updated_at = now()")
                .param("a", accountId)
                .param("c", emailCiphertext)
                .update();
    }

    @Override
    public void disable(UUID accountId) {
        jdbc.sql(
                        "UPDATE identity.notification_email SET enabled = FALSE, updated_at = now()"
                                + " WHERE account_id = :a")
                .param("a", accountId)
                .update();
    }
}
