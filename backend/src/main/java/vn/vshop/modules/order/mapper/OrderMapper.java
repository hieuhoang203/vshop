package vn.vshop.modules.order.mapper;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.modules.order.dto.response.CheckoutGroupResponse;
import vn.vshop.modules.order.dto.response.OrderResponse;
import vn.vshop.modules.order.dto.response.OrderSummaryResponse;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.model.GroupDetail;
import vn.vshop.modules.order.model.OrderDetail;
import vn.vshop.modules.order.model.OrderSummary;

public final class OrderMapper {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};

    private OrderMapper() {}

    public static CheckoutGroupResponse toResponse(GroupDetail detail) {
        var g = detail.group();
        return new CheckoutGroupResponse(
                g.id(),
                g.paymentMethod(),
                g.currency(),
                g.totalAmount(),
                g.createdAt(),
                detail.orders().stream().map(OrderMapper::toResponse).toList());
    }

    public static OrderResponse toResponse(OrderDetail detail) {
        var o = detail.order();
        var r = detail.recipient();
        return new OrderResponse(
                o.id(),
                o.orderNumber(),
                o.checkoutGroupId(),
                o.shopId(),
                o.shopNameSnapshot(),
                o.status(),
                o.itemAmount(),
                o.discountAmount(),
                o.shippingAmount(),
                o.payableAmount(),
                new OrderResponse.OrderRecipientResponse(
                        r.name(), r.phone(), r.street(), r.ward(), r.province()),
                o.paymentDeadline(),
                o.canceledAt(),
                o.cancelReason(),
                o.createdAt(),
                detail.lines().stream().map(OrderMapper::toResponse).toList(),
                detail.history().stream().map(OrderMapper::toResponse).toList());
    }

    public static OrderSummaryResponse toResponse(OrderSummary s) {
        return new OrderSummaryResponse(
                s.id(),
                s.orderNumber(),
                s.checkoutGroupId(),
                s.shopNameSnapshot(),
                s.status(),
                s.payableAmount(),
                s.itemCount(),
                s.firstProductName(),
                s.firstImageKey(),
                s.createdAt());
    }

    private static OrderResponse.OrderLineResponse toResponse(OrderLine l) {
        Map<String, String> variant = JSON.readValue(l.variantSnapshot(), STRINGS);
        return new OrderResponse.OrderLineResponse(
                l.skuId(),
                l.productId(),
                l.productSlug(),
                l.productNameSnapshot(),
                l.imageKey(),
                l.sellerSku(),
                variant.get("label"),
                l.quantity(),
                l.unitPrice(),
                l.grossAmount(),
                l.discountAmount(),
                l.netAmount());
    }

    private static OrderResponse.OrderStatusEventResponse toResponse(OrderStatusEvent e) {
        return new OrderResponse.OrderStatusEventResponse(
                e.fromStatus(), e.toStatus(), e.actorType(), e.reason(), e.createdAt());
    }
}
