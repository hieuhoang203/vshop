package vn.vshop.modules.order.gateway;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * What the order module needs from a shipping carrier. {@code createShipment}, {@code lookup} and
 * {@code cancel} call the carrier over the network and must never run inside a database
 * transaction. Parsing and verifying a webhook are pure.
 */
public interface ShippingCarrier {
    String code();

    /** False until the carrier credentials are configured. */
    boolean configured();

    /** Idempotent per {@link CreateRequest#partnerOrderId()}: a repeat reports DUPLICATE. */
    CreateResult createShipment(CreateRequest request);

    /** Finds the tracking number the carrier gave to our own order id, if it knows it. */
    String lookup(String partnerOrderId);

    CancelResult cancel(String trackingNumber);

    /** Constant-time check of the secret the carrier sends with each webhook. */
    boolean verifyWebhookToken(String token);

    /** Extracts the facts from a webhook body (JSON or form fields). */
    Event parseWebhook(Map<String, ?> payload);

    record Pickup(
            String name,
            String phone,
            String address,
            String ward,
            String district,
            String province) {}

    record Recipient(String name, String phone, String address, String ward, String province) {}

    record Item(String name, int quantity, int weightGrams) {}

    /**
     * {@code codAmount} is what the courier collects from the recipient (0 when already paid);
     * {@code declaredValue} is the goods value for insurance.
     */
    record CreateRequest(
            String partnerOrderId,
            long codAmount,
            long declaredValue,
            Pickup pickup,
            Recipient recipient,
            List<Item> items,
            String note) {}

    enum Outcome {
        CREATED,
        DUPLICATE,
        REJECTED,
        /** No usable answer (timeout, 5xx): the carrier may or may not have the order. */
        UNKNOWN
    }

    record CreateResult(
            Outcome outcome, String trackingNumber, long fee, long insuranceFee, String message) {}

    record CancelResult(boolean canceled, String message) {}

    record Event(
            String trackingNumber,
            String partnerOrderId,
            int statusId,
            String reasonCode,
            String reason,
            OffsetDateTime occurredAt) {}
}
