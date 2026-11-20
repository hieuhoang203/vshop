package vn.vshop.modules.platform.entity;

/** Business parameters that need an explicit owner approval before checkout may use them. */
public enum PolicyType {
    SHIPPING_FLAT,
    RETURN,
    COD_LIMITS,
    PAYMENT_WINDOW,
    SELLER_CONFIRMATION
}
