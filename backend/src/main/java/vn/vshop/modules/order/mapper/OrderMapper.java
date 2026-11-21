package vn.vshop.modules.order.mapper;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.modules.order.dto.response.AdminRefundResponse;
import vn.vshop.modules.order.dto.response.CheckoutGroupResponse;
import vn.vshop.modules.order.dto.response.OrderResponse;
import vn.vshop.modules.order.dto.response.OrderSummaryResponse;
import vn.vshop.modules.order.dto.response.OverdueOrdersResponse;
import vn.vshop.modules.order.dto.response.RefundResponse;
import vn.vshop.modules.order.dto.response.ReturnCaseResponse;
import vn.vshop.modules.order.dto.response.SellerOrderResponse;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.model.AdminRefund;
import vn.vshop.modules.order.model.GroupDetail;
import vn.vshop.modules.order.model.OrderDetail;
import vn.vshop.modules.order.model.OrderSummary;
import vn.vshop.modules.order.model.OverdueOrders;
import vn.vshop.modules.order.model.RefundView;
import vn.vshop.modules.order.model.ReturnCaseDetail;
import vn.vshop.modules.order.model.SellerOrderDetail;

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
                detail.paymentUrl(),
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
                detail.history().stream().map(OrderMapper::toResponse).toList(),
                detail.refund() == null ? null : toResponse(detail.refund()));
    }

    public static ReturnCaseResponse toResponse(ReturnCaseDetail d) {
        var c = d.returnCase();
        return new ReturnCaseResponse(
                c.id(),
                c.sellerOrderId(),
                c.orderNumber(),
                c.status(),
                c.reasonCode(),
                c.preferredResolution(),
                c.resolution(),
                c.description(),
                c.requestedAmount(),
                c.offeredAmount(),
                c.approvedAmount(),
                c.sellerDeadlineAt(),
                c.sellerNote(),
                c.adminNote(),
                c.returnTrackingCode(),
                c.createdAt(),
                d.events().stream()
                        .map(
                                e ->
                                        new ReturnCaseResponse.ReturnCaseEventResponse(
                                                e.fromStatus(),
                                                e.toStatus(),
                                                e.actorType(),
                                                e.note(),
                                                e.createdAt()))
                        .toList(),
                d.items().stream()
                        .map(
                                i ->
                                        new ReturnCaseResponse.ReturnCaseItemResponse(
                                                i.orderLineId(),
                                                i.productName(),
                                                i.quantity(),
                                                i.amount()))
                        .toList());
    }

    public static RefundResponse toResponse(RefundView r) {
        return new RefundResponse(
                r.id(),
                r.status(),
                r.reason(),
                r.amount(),
                r.needsAccount(),
                r.bankName(),
                r.accountLast4(),
                r.payoutReference(),
                r.createdAt(),
                r.paidAt());
    }

    public static AdminRefundResponse toResponse(AdminRefund r) {
        return new AdminRefundResponse(
                r.id(),
                r.orderId(),
                r.orderNumber(),
                r.reason(),
                r.status(),
                r.amount(),
                r.bankName(),
                r.accountNumber(),
                r.accountName(),
                r.payoutReference(),
                r.note(),
                r.createdAt(),
                r.paidAt(),
                r.canPayAutomatically(),
                r.payoutFailure());
    }

    public static SellerOrderResponse toResponse(SellerOrderDetail d) {
        var o = d.order();
        var r = d.recipient();
        return new SellerOrderResponse(
                o.id(),
                o.orderNumber(),
                o.status(),
                o.version(),
                o.itemAmount(),
                o.shippingAmount(),
                o.payableAmount(),
                new SellerOrderResponse.SellerRecipientResponse(
                        r.name(), r.phone(), r.street(), r.ward(), r.province()),
                o.manualTrackingCode(),
                o.cancelReason(),
                o.createdAt(),
                d.lines().stream().map(OrderMapper::toResponse).toList(),
                d.history().stream().map(OrderMapper::toResponse).toList());
    }

    public static OverdueOrdersResponse toResponse(OverdueOrders o) {
        return new OverdueOrdersResponse(
                o.confirmMinutes(),
                o.items().stream()
                        .map(
                                i ->
                                        new OverdueOrdersResponse.OverdueOrderResponse(
                                                i.id(),
                                                i.orderNumber(),
                                                i.shopId(),
                                                i.shopName(),
                                                i.payableAmount(),
                                                i.createdAt(),
                                                i.minutesOverdue()))
                        .toList());
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
                l.id(),
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
