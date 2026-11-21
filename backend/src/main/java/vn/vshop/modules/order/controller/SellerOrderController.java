package vn.vshop.modules.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.order.dto.request.ConfirmReceivedRequest;
import vn.vshop.modules.order.dto.request.ReasonRequest;
import vn.vshop.modules.order.dto.request.ShipOrderRequest;
import vn.vshop.modules.order.dto.response.SellerOrderResponse;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.OrderFulfillmentService;

@RestController
@RequestMapping("/api/v1/seller/shops/{shopId}/orders")
public class SellerOrderController {
    private final OrderFulfillmentService service;

    public SellerOrderController(OrderFulfillmentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SellerOrderResponse> list(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        List<SellerOrderResponse> items =
                service.list(jwt.getSubject(), shopId, status, page, size).stream()
                        .map(OrderMapper::toResponse)
                        .toList();
        return new PageResponse<>(
                items, service.count(jwt.getSubject(), shopId, status), page, size);
    }

    @GetMapping("/{orderId}")
    public SellerOrderResponse get(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.get(jwt.getSubject(), shopId, orderId));
    }

    @PostMapping("/{orderId}/confirm")
    public SellerOrderResponse confirm(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.confirm(jwt.getSubject(), shopId, orderId));
    }

    /** The carrier brought an undeliverable parcel back: cancel the order, refund, restock. */
    @PostMapping("/{orderId}/parcel-returned")
    public SellerOrderResponse parcelReturned(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmReceivedRequest request) {
        return OrderMapper.toResponse(
                service.confirmParcelReturned(
                        jwt.getSubject(), shopId, orderId, request.restock()));
    }

    @PostMapping("/{orderId}/reject")
    public SellerOrderResponse reject(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest request) {
        return OrderMapper.toResponse(
                service.reject(jwt.getSubject(), shopId, orderId, request.reason()));
    }

    @PostMapping("/{orderId}/pack")
    public SellerOrderResponse pack(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.startPacking(jwt.getSubject(), shopId, orderId));
    }

    @PostMapping("/{orderId}/ready")
    public SellerOrderResponse ready(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.markReady(jwt.getSubject(), shopId, orderId));
    }

    @PostMapping("/{orderId}/ship")
    public SellerOrderResponse ship(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ShipOrderRequest request) {
        return OrderMapper.toResponse(
                service.ship(jwt.getSubject(), shopId, orderId, request.trackingCode()));
    }

    @PostMapping("/{orderId}/delivered")
    public SellerOrderResponse delivered(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.declareDelivered(jwt.getSubject(), shopId, orderId));
    }
}
