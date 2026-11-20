package vn.vshop.modules.platform.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.platform.entity.Notification;
import vn.vshop.modules.platform.repository.NotificationRepository;

/**
 * In-app notifications. Other modules create them through {@link #notify}; people read only their
 * own, scoped by the account taken from the token.
 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final AccountService accounts;
    private final EmailDeliveryService emails;

    NotificationService(
            NotificationRepository notifications,
            AccountService accounts,
            EmailDeliveryService emails) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.emails = emails;
    }

    /** Idempotent per recipient and dedupe key. */
    @Transactional
    public void notify(
            UUID recipient, String kind, String title, String body, String link, String dedupeKey) {
        notifications
                .insert(recipient, kind, title, body, link, dedupeKey)
                .ifPresent(id -> emails.enqueue(id, recipient));
    }

    @Transactional
    public List<Notification> list(String subject, boolean unreadOnly, int page, int size) {
        return notifications.list(
                accounts.requireActiveAccount(subject), unreadOnly, size, page * size);
    }

    @Transactional
    public long count(String subject, boolean unreadOnly) {
        return notifications.count(accounts.requireActiveAccount(subject), unreadOnly);
    }

    @Transactional
    public void markRead(String subject, UUID id) {
        if (!notifications.markRead(id, accounts.requireActiveAccount(subject))) {
            throw new BusinessException(
                    ErrorCode.NOTIFICATION_NOT_FOUND, "Không tìm thấy thông báo.");
        }
    }

    @Transactional
    public int markAllRead(String subject) {
        return notifications.markAllRead(accounts.requireActiveAccount(subject));
    }
}
