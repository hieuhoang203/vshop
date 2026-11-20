package vn.vshop.modules.checkout.dto.request;

import java.util.UUID;

/** Optional body of POST /checkout/quotes: the buyer's delivery address to bind the quote to. */
public record CreateQuoteRequest(UUID addressId) {}
