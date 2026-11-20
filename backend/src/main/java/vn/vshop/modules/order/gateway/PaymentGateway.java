package vn.vshop.modules.order.gateway;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * What the order module needs from an online payment provider. {@link #createSession} and {@link
 * #cancelSession} may call the provider over the network and must never run inside a database
 * transaction; {@link #verify} is a pure check of a callback.
 */
public interface PaymentGateway {
    String provider();

    /** False until the merchant credentials are configured; online payment is then refused. */
    boolean configured();

    /** Creates the hosted payment page for one attempt. */
    SessionResult createSession(Session session);

    /** Best effort: closes a payment page we will not use. Never throws. */
    default void cancelSession(String providerReference) {}

    /** Checks the signature of a callback (query parameters or JSON body) and extracts facts. */
    Callback verify(Map<String, ?> payload);

    record Session(
            String merchantReference,
            long amountVnd,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String clientIp,
            String orderInfo,
            String returnUrl) {}

    enum Outcome {
        CREATED,
        /** The provider already has this reference but we have no page for it. */
        DUPLICATE,
        FAILED
    }

    record SessionResult(Outcome outcome, String url, String providerReference, String message) {}

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
