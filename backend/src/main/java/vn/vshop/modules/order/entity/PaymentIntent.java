package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The amount a checkout group owes online. Status: CREATED, PAID, UNKNOWN, FAILED, CANCELED. */
public record PaymentIntent(
        UUID id,
        UUID checkoutGroupId,
        String merchantReference,
        long requestedAmount,
        String status,
        OffsetDateTime paymentDeadline,
        OffsetDateTime createdAt) {}
