package vn.vshop.modules.checkout.model;

/** A buyer's cart priced right now. */
public record CartView(long version, PricedCart cart) {}
