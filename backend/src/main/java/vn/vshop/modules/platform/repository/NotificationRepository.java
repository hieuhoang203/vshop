package vn.vshop.modules.platform.repository;

import java.util.List;
import java.util.UUID;
import vn.vshop.modules.platform.entity.Notification;

public interface NotificationRepository {
    /** False when the same dedupe key already exists for the recipient (a repeated event). */
    java.util.Optional<UUID> insert(
            UUID recipient, String kind, String title, String body, String link, String dedupeKey);

    List<Notification> list(UUID recipient, boolean unreadOnly, int limit, int offset);

    long count(UUID recipient, boolean unreadOnly);

    boolean markRead(UUID id, UUID recipient);

    int markAllRead(UUID recipient);
}
