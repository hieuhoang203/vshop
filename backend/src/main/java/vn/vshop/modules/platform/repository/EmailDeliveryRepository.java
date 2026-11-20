package vn.vshop.modules.platform.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EmailDeliveryRepository {
    /** An email waiting to go out, with the notification text it carries. */
    record Due(UUID id, UUID accountId, int attempts, String title, String body, String link) {}

    void insert(UUID notificationId, UUID accountId);

    /**
     * Claims emails that are due (or whose sender crashed while sending), locks them for a while
     * and counts the attempt. Two workers never claim the same email.
     */
    List<Due> claimDue(int limit);

    void markSent(UUID id);

    void markSkipped(UUID id, String reason);

    void retryLater(UUID id, String error, OffsetDateTime at);

    void markFailed(UUID id, String error);
}
