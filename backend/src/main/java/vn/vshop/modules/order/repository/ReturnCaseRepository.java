package vn.vshop.modules.order.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.order.entity.ReturnCase;
import vn.vshop.modules.order.entity.ReturnCaseEvent;

public interface ReturnCaseRepository {
    /** What a transition may change; null leaves a column as it is. */
    record Change(
            Long offeredAmount,
            Long approvedAmount,
            String resolution,
            String sellerNote,
            String adminNote,
            String trackingCode) {
        public static Change none() {
            return new Change(null, null, null, null, null, null);
        }
    }

    void insert(
            UUID id,
            UUID orderId,
            UUID shopId,
            UUID buyer,
            String reason,
            String preferred,
            String description,
            long requestedAmount,
            UUID policyId,
            OffsetDateTime sellerDeadline);

    Optional<ReturnCase> find(UUID id);

    Optional<ReturnCase> findLiveByOrder(UUID orderId);

    List<ReturnCase> listForShop(UUID shopId, String status, int limit, int offset);

    long countForShop(UUID shopId, String status);

    /** Cases an administrator must act on: REVIEWING, and REQUESTED past the shop's deadline. */
    List<ReturnCase> listForAdmin(OffsetDateTime now, int limit);

    /**
     * Moves the case to {@code to} only while it is in one of {@code from}; false means somebody
     * else changed it first.
     */
    boolean move(UUID id, Collection<String> from, String to, Change change);

    void insertEvent(
            UUID caseId, String from, String to, String actorType, String actor, String note);

    List<ReturnCaseEvent> events(UUID caseId);
}
