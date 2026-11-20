package vn.vshop.modules.checkout.model;

/** Why a cart line cannot be bought right now. Absent (null) means it can. */
public enum CartLineIssue {
    /** SKU or product is not on sale, or the shop is not approved. */
    UNAVAILABLE,
    OUT_OF_STOCK,
    /** Fewer units are sellable than requested; see availableQuantity. */
    INSUFFICIENT_STOCK
}
