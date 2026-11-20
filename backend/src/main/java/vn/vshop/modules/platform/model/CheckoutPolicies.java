package vn.vshop.modules.platform.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.vshop.modules.platform.entity.PolicyType;

/**
 * The approved policies checkout depends on. A null member means no approved version exists, so
 * checkout must not run (docs/20 section 2).
 */
public record CheckoutPolicies(
        ShippingFlat shipping, ReturnWindow returns, CodLimits cod, PaymentWindow payment) {

    public record ShippingFlat(UUID policyId, long amount) {}

    public record ReturnWindow(UUID policyId, int windowDays) {}

    public record CodLimits(UUID policyId, long maxOrderAmount, int maxOpenOrders) {}

    public record PaymentWindow(UUID policyId, int paymentMinutes, int reservationBufferMinutes) {}

    public List<PolicyType> missing() {
        var missing = new ArrayList<PolicyType>();
        if (shipping == null) missing.add(PolicyType.SHIPPING_FLAT);
        if (returns == null) missing.add(PolicyType.RETURN);
        if (cod == null) missing.add(PolicyType.COD_LIMITS);
        if (payment == null) missing.add(PolicyType.PAYMENT_WINDOW);
        return missing;
    }

    public boolean complete() {
        return missing().isEmpty();
    }
}
