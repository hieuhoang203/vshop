package vn.vshop.modules.order.gateway;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * What the order module needs from an online payment provider. Building the payment URL and
 * verifying a callback are pure computations (no network), so they are safe inside a database
 * transaction. Calling the provider over the network (status query, refund) is not part of this
 * port yet.
 */
public interface PaymentGateway {
    String provider();

    /** False until the merchant credentials are configured; online payment is then refused. */
    boolean configured();

    String paymentUrl(Session session);

    /** Checks the signature of a callback and extracts the facts the order module needs. */
    Callback verify(Map<String, String> parameters);

    record Session(
            String merchantReference,
            long amountVnd,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String clientIp,
            String orderInfo,
            String returnUrl) {}

    /**
     * {@code validSignature} false means nothing else may be trusted. {@code success} is true only
     * when the provider reports the payment itself as successful.
     */
    record Callback(
            boolean validSignature,
            String merchantReference,
            long amountVnd,
            boolean success,
            String providerTransactionNo,
            String responseCode) {}
}
