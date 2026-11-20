package vn.vshop.modules.checkout.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code current} is false once the quote expired or the cart changed after it was calculated. */
public record QuoteResponse(
        UUID id,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        boolean expired,
        boolean current,
        CartResponse content) {}
