package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A parcel handed to a carrier for one seller order. {@code status}: CREATING (request sent, answer
 * not recorded), CREATED, FAILED, PICKED_UP, IN_TRANSIT, DELIVERED, DELIVERY_FAILED, RETURNING,
 * RETURNED, CANCELED.
 */
public record Shipment(
        UUID id,
        UUID sellerOrderId,
        UUID shopId,
        String carrierCode,
        String partnerOrderId,
        String trackingNumber,
        String status,
        Integer carrierStatusId,
        long codAmount,
        int weightGrams,
        long declaredValue,
        Long carrierFee,
        Long insuranceFee,
        String failureMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
