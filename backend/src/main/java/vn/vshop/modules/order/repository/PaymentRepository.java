package vn.vshop.modules.order.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.order.entity.PaymentAttempt;
import vn.vshop.modules.order.entity.PaymentIntent;

public interface PaymentRepository {
    void insertIntent(
            UUID id, UUID groupId, String reference, long amount, OffsetDateTime deadline);

    void insertAttempt(
            UUID id,
            UUID intentId,
            UUID groupId,
            String provider,
            String reference,
            String clientIp,
            OffsetDateTime expiresAt);

    Optional<PaymentIntent> findIntentByGroup(UUID groupId);

    /** Next unique integer reference for providers that need one (payOS order code). */
    long nextOrderCode();

    /** Stores the page URL once; false when another request already stored one. */
    boolean setCheckout(UUID attemptId, String url, String providerReference);

    Optional<PaymentIntent> findIntentForUpdate(UUID intentId);

    Optional<PaymentAttempt> findLatestAttempt(UUID intentId);

    Optional<PaymentAttempt> findAttemptByReferenceForUpdate(String reference);

    void setIntentStatus(UUID intentId, String status);

    void setAttemptStatus(UUID attemptId, String status, String providerReference);

    /** False when the reference from the provider was already recorded (a repeated delivery). */
    boolean insertCharge(
            UUID id,
            UUID attemptId,
            UUID groupId,
            String provider,
            String providerReference,
            long amount);

    void insertAllocation(UUID id, UUID chargeId, UUID orderId, UUID groupId, long amount);

    boolean insertEvent(String provider, String eventKey, String reference, String outcome);

    void insertReview(UUID chargeId, UUID groupId, String reason);

    /** Unpaid intents past their deadline, locked so two workers never take the same one. */
    List<PaymentIntent> lockOverdueIntents(OffsetDateTime now, int limit);
}
