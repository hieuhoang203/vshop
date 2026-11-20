package vn.vshop.modules.order.model;

import java.util.List;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.SellerOrder;

/** An order with its lines, status history and the buyer's delivery details (decrypted). */
public record OrderDetail(
        SellerOrder order,
        List<OrderLine> lines,
        List<OrderStatusEvent> history,
        RecipientView recipient) {
    public OrderDetail {
        lines = List.copyOf(lines);
        history = List.copyOf(history);
    }

    public record RecipientView(
            String name, String phone, String street, String ward, String province) {}
}
