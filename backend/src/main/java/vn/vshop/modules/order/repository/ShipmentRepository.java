package vn.vshop.modules.order.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.order.entity.PickupAddress;
import vn.vshop.modules.order.entity.Shipment;

public interface ShipmentRepository {
    Optional<PickupAddress> findPickup(UUID shopId);

    void savePickup(PickupAddress address);

    /** The newest shipment of the order that is not FAILED or CANCELED. */
    Optional<Shipment> findLiveByOrder(UUID orderId);

    /** The newest FAILED shipment, which a new request reuses (same partner order id). */
    Optional<Shipment> findFailedByOrder(UUID orderId);

    Optional<Shipment> findByTracking(String carrier, String trackingNumber);

    Optional<Shipment> findByPartnerOrder(String carrier, String partnerOrderId);

    long countByOrder(UUID orderId);

    void insertCreating(
            UUID id,
            UUID orderId,
            UUID shopId,
            String carrier,
            String partnerOrderId,
            long codAmount,
            int weightGrams,
            long declaredValue);

    void reopen(UUID id, long codAmount, int weightGrams, long declaredValue);

    void markCreated(UUID id, String trackingNumber, Long fee, Long insuranceFee);

    void markFailed(UUID id, String message);

    /** Sets the status unless the shipment already reached an end state. */
    void advance(UUID id, String status, int carrierStatusId);

    void cancel(UUID id);

    boolean insertEvent(
            UUID shipmentId,
            String eventId,
            String rawStatus,
            String normalized,
            String reasonCode,
            String reason,
            OffsetDateTime occurredAt);
}
