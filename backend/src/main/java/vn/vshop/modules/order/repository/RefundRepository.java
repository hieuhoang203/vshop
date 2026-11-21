package vn.vshop.modules.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.order.entity.Refund;

public interface RefundRepository {
    /** Money collected for one order that has not been refunded yet, with the rows locked. */
    record Refundable(UUID allocationId, UUID chargeId, UUID checkoutGroupId, long amount) {}

    /**
     * Allocations of the order with what is still refundable, locked until the transaction ends so
     * two refunds of the same money cannot both be created.
     */
    List<Refundable> lockRefundable(UUID orderId);

    /** False when a refund with this idempotency key exists (a repeat). */
    boolean insertRefund(
            UUID id,
            UUID chargeId,
            UUID groupId,
            UUID orderId,
            String reason,
            String idempotencyKey,
            long amount,
            String source,
            UUID returnCaseId);

    /**
     * Cash-on-delivery money of the order not refunded yet (payable minus refunds that are not
     * FAILED), with the order row locked. Empty when the order does not exist.
     */
    java.util.OptionalLong lockCodRefundable(UUID orderId);

    UUID groupOf(UUID orderId);

    void insertAllocation(UUID refundId, UUID chargeId, UUID allocationId, long amount);

    List<Refund> findByOrder(UUID orderId);

    Optional<Refund> find(UUID refundId);

    List<Refund> list(String status, int limit);

    /** What a payout needs, for a refund that is SUBMITTED. */
    record PayoutTarget(
            long amount,
            String bankBin,
            String accountNumberCiphertext,
            String accountNameCiphertext,
            String providerReference) {}

    /** APPROVED to SUBMITTED, once; needs a bank BIN. False when it is not in that state. */
    boolean claimPayout(UUID id, String provider, String providerReference);

    Optional<PayoutTarget> payoutTarget(UUID id);

    /** SUBMITTED to SUCCEEDED, once. */
    boolean payoutSucceeded(UUID id, String reference, String subject);

    /** SUBMITTED back to APPROVED with the reason; the account is kept for another try. */
    boolean payoutFailed(UUID id, String reason);

    /** Refunds submitted to the provider and not resolved for at least {@code seconds}. */
    List<UUID> staleSubmitted(int seconds, int limit);

    /** AWAITING_ACCOUNT or APPROVED to APPROVED with the given destination. */
    boolean setAccount(
            UUID id,
            String bankName,
            String bankBin,
            String numberCiphertext,
            String nameCiphertext);

    /** APPROVED to SUCCEEDED, once. */
    boolean markPaid(UUID id, String payoutReference, String note, String subject);

    /** APPROVED back to AWAITING_ACCOUNT: the account was wrong, the buyer must enter it again. */
    boolean badAccount(UUID id, String note);
}
