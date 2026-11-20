package vn.vshop.modules.checkout.model;

import java.util.UUID;

/**
 * A quote that passed every checkout check against the cart as it is right now, with the cart
 * priced again at this instant. The order is built from {@code priced}, never from client input.
 */
public record OrderBasis(
        UUID buyerAccountId, UUID cartId, UUID quoteId, UUID addressId, PricedCart priced) {}
