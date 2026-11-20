package vn.vshop.modules.checkout.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A stored quote. {@code current} is true only while the quote has not expired and the cart has not
 * changed since it was calculated; checkout must require it.
 */
public record QuoteView(
        UUID id,
        long cartVersion,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        boolean expired,
        boolean current,
        PricedCart cart) {}
