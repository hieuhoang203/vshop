package vn.vshop.modules.order.model;

import java.util.List;
import vn.vshop.modules.order.entity.CheckoutGroup;

/** {@code replayed} is true when an idempotent retry returned an earlier result. */
public record GroupDetail(
        CheckoutGroup group, List<OrderDetail> orders, boolean replayed, String paymentUrl) {
    public GroupDetail {
        orders = List.copyOf(orders);
    }

    public GroupDetail withPaymentUrl(String url) {
        return new GroupDetail(group, orders, replayed, url);
    }
}
