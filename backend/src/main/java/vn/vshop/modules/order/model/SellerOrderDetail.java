package vn.vshop.modules.order.model;

import java.util.List;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.SellerOrder;

/**
 * An order as its shop sees it. The buyer's name, phone and street are {@code null} until the shop
 * has confirmed the order; before that only the region is shown.
 */
public record SellerOrderDetail(
        SellerOrder order,
        List<OrderLine> lines,
        List<OrderStatusEvent> history,
        OrderDetail.RecipientView recipient) {
    public SellerOrderDetail {
        lines = List.copyOf(lines);
        history = List.copyOf(history);
    }
}
