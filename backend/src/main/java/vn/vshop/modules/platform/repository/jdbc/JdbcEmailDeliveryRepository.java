package vn.vshop.modules.platform.repository.jdbc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.platform.repository.EmailDeliveryRepository;

@Repository
public class JdbcEmailDeliveryRepository implements EmailDeliveryRepository {
    private final JdbcClient jdbc;

    public JdbcEmailDeliveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID notificationId, UUID accountId) {
        jdbc.sql(
                        "INSERT INTO platform.email_delivery (id, notification_id, account_id)"
                                + " VALUES (:id, :n, :a) ON CONFLICT (notification_id) DO NOTHING")
                .param("id", UUID.randomUUID())
                .param("n", notificationId)
                .param("a", accountId)
                .update();
    }

    @Override
    public List<Due> claimDue(int limit) {
        return jdbc.sql(
                        """
            UPDATE platform.email_delivery d
            SET status = 'SENDING', attempts = d.attempts + 1,
                locked_until = now() + interval '2 minutes'
            WHERE d.id IN (
                SELECT id FROM platform.email_delivery
                WHERE (status = 'PENDING' AND next_attempt_at <= now())
                   OR (status = 'SENDING' AND locked_until < now())
                ORDER BY next_attempt_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED)
            RETURNING d.id, d.account_id, d.attempts,
                (SELECT n.title FROM platform.notification n WHERE n.id = d.notification_id) AS title,
                (SELECT n.body FROM platform.notification n WHERE n.id = d.notification_id) AS body,
                (SELECT n.link FROM platform.notification n WHERE n.id = d.notification_id) AS link
            """)
                .param("limit", limit)
                .query(Due.class)
                .list();
    }

    @Override
    public void markSent(UUID id) {
        jdbc.sql(
                        "UPDATE platform.email_delivery SET status = 'SENT', sent_at = now(),"
                                + " locked_until = NULL, last_error = NULL WHERE id = :id")
                .param("id", id)
                .update();
    }

    @Override
    public void markSkipped(UUID id, String reason) {
        jdbc.sql(
                        "UPDATE platform.email_delivery SET status = 'SKIPPED', locked_until = NULL,"
                                + " last_error = :r WHERE id = :id")
                .param("r", reason)
                .param("id", id)
                .update();
    }

    @Override
    public void retryLater(UUID id, String error, OffsetDateTime at) {
        jdbc.sql(
                        "UPDATE platform.email_delivery SET status = 'PENDING', locked_until = NULL,"
                                + " last_error = :e, next_attempt_at = :at WHERE id = :id")
                .param("e", cut(error))
                .param("at", at)
                .param("id", id)
                .update();
    }

    @Override
    public void markFailed(UUID id, String error) {
        jdbc.sql(
                        "UPDATE platform.email_delivery SET status = 'FAILED', locked_until = NULL,"
                                + " last_error = :e WHERE id = :id")
                .param("e", cut(error))
                .param("id", id)
                .update();
    }

    private static String cut(String s) {
        return s == null ? "" : s.substring(0, Math.min(300, s.length()));
    }
}
