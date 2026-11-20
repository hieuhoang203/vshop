package vn.vshop.modules.platform.repository.jdbc;

import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.platform.entity.Notification;
import vn.vshop.modules.platform.repository.NotificationRepository;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {
    private final JdbcClient jdbc;

    public JdbcNotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Optional<UUID> insert(
            UUID recipient, String kind, String title, String body, String link, String dedupeKey) {
        UUID id = UUID.randomUUID();
        boolean fresh =
                jdbc.sql(
                                        "INSERT INTO platform.notification (id, recipient_account_id, kind,"
                                                + " title, body, link, dedupe_key)"
                                                + " VALUES (:id, :recipient, :kind, :title, :body, :link, :key)"
                                                + " ON CONFLICT (recipient_account_id, dedupe_key) DO NOTHING")
                                .param("id", id)
                                .param("recipient", recipient)
                                .param("kind", kind)
                                .param("title", title)
                                .param("body", body)
                                .param("link", link, Types.VARCHAR)
                                .param("key", dedupeKey)
                                .update()
                        == 1;
        return fresh ? java.util.Optional.of(id) : java.util.Optional.empty();
    }

    @Override
    public List<Notification> list(UUID recipient, boolean unreadOnly, int limit, int offset) {
        return jdbc.sql(
                        "SELECT id, kind, title, body, link, read_at, created_at"
                                + " FROM platform.notification WHERE recipient_account_id = :r"
                                + " AND (:unread = false OR read_at IS NULL)"
                                + " ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
                .param("r", recipient)
                .param("unread", unreadOnly)
                .param("limit", limit)
                .param("offset", offset)
                .query(Notification.class)
                .list();
    }

    @Override
    public long count(UUID recipient, boolean unreadOnly) {
        return jdbc.sql(
                        "SELECT count(*) FROM platform.notification WHERE recipient_account_id = :r"
                                + " AND (:unread = false OR read_at IS NULL)")
                .param("r", recipient)
                .param("unread", unreadOnly)
                .query(Long.class)
                .single();
    }

    @Override
    public boolean markRead(UUID id, UUID recipient) {
        return jdbc.sql(
                                "UPDATE platform.notification SET read_at = COALESCE(read_at, now())"
                                        + " WHERE id = :id AND recipient_account_id = :r")
                        .param("id", id)
                        .param("r", recipient)
                        .update()
                == 1;
    }

    @Override
    public int markAllRead(UUID recipient) {
        return jdbc.sql(
                        "UPDATE platform.notification SET read_at = now()"
                                + " WHERE recipient_account_id = :r AND read_at IS NULL")
                .param("r", recipient)
                .update();
    }
}
