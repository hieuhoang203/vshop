package vn.vshop.modules.order.gateway;

/**
 * Sends money out to a bank account (a refund). {@code submit} and {@code query} call the provider
 * over the network and must never run inside a database transaction. Money moves for real, so an
 * answer that is not clearly a success or a failure is {@link Outcome#UNKNOWN} and is resolved with
 * {@code query} using the same reference, never by sending the transfer again.
 */
public interface PayoutGateway {
    String provider();

    /** True only when payouts are switched on and the credentials are present. */
    boolean enabled();

    /** Idempotent per {@link Request#referenceId()}: a repeat reports the existing transfer. */
    Result submit(Request request);

    Result query(String referenceId);

    record Request(
            String referenceId,
            long amountVnd,
            String description,
            String bankBin,
            String accountNumber,
            String accountName) {}

    enum Outcome {
        /** Money reached the account. */
        SUCCEEDED,
        /** The provider has the request and has not finished. */
        PROCESSING,
        /** The provider refused or reversed the transfer; nothing was sent. */
        FAILED,
        /** No usable answer (timeout, 5xx): the transfer may or may not exist. */
        UNKNOWN
    }

    record Result(Outcome outcome, String providerReference, String message) {}
}
