package vn.vshop.modules.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.order.dto.request.PlaceOrderRequest;
import vn.vshop.modules.order.dto.request.ReasonRequest;
import vn.vshop.modules.order.dto.request.RefundAccountRequest;
import vn.vshop.modules.order.dto.response.CheckoutGroupResponse;
import vn.vshop.modules.order.dto.response.OrderResponse;
import vn.vshop.modules.order.dto.response.OrderSummaryResponse;
import vn.vshop.modules.order.dto.response.RefundResponse;
import vn.vshop.modules.order.entity.PaymentMethod;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.model.GroupDetail;
import vn.vshop.modules.order.service.OrderFulfillmentService;
import vn.vshop.modules.order.service.OrderService;
import vn.vshop.modules.order.service.PaymentService;
import vn.vshop.modules.order.service.RefundService;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderService service;

    private final OrderFulfillmentService fulfillment;
    private final PaymentService payments;
    private final RefundService refunds;

    public OrderController(
            OrderService service,
            OrderFulfillmentService fulfillment,
            PaymentService payments,
            RefundService refunds) {
        this.refunds = refunds;
        this.payments = payments;
        this.service = service;
        this.fulfillment = fulfillment;
    }

    /**
     * Places orders from a quote. Idempotent per buyer and key: a retry returns the earlier result
     * (200) instead of creating more orders (201 on first success).
     */
    @PostMapping("/checkout/orders")
    public ResponseEntity<CheckoutGroupResponse> place(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}")
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            jakarta.servlet.http.HttpServletRequest http,
            @Valid @RequestBody PlaceOrderRequest request) {
        var result =
                service.place(
                        jwt.getSubject(),
                        request.quoteId(),
                        request.paymentMethod(),
                        idempotencyKey,
                        clientIp(http));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(OrderMapper.toResponse(withPage(result)));
    }

    /**
     * Adds the payment page of an online group. Creating the page may call the provider, so it
     * happens here, after the placing transaction has committed.
     */
    private GroupDetail withPage(GroupDetail detail) {
        if (detail.group().paymentMethod() != PaymentMethod.PSP) {
            return detail;
        }
        return detail.withPaymentUrl(payments.ensureSession(detail.group()).orElse(null));
    }

    /** First address of X-Forwarded-For when a proxy set it, else the socket peer. */
    private static String clientIp(jakarta.servlet.http.HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? http.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }

    @GetMapping("/checkout/groups/{id}")
    public CheckoutGroupResponse group(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(withPage(service.group(jwt.getSubject(), id)));
    }

    @GetMapping("/orders")
    public PageResponse<OrderSummaryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        List<OrderSummaryResponse> items =
                service.list(jwt.getSubject(), page, size).stream()
                        .map(OrderMapper::toResponse)
                        .toList();
        return new PageResponse<>(items, service.count(jwt.getSubject()), page, size);
    }

    /** The buyer can cancel only until the shop starts on the order; the stock goes back. */
    @PostMapping("/orders/{id}/cancel")
    public OrderResponse cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest request) {
        return OrderMapper.toResponse(
                fulfillment.cancelByBuyer(jwt.getSubject(), id, request.reason()));
    }

    /** Where to send the refund of this order; allowed until it has been paid out. */
    @PutMapping("/orders/{id}/refund-account")
    public RefundResponse refundAccount(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RefundAccountRequest request) {
        return OrderMapper.toResponse(
                refunds.provideAccount(
                        jwt.getSubject(),
                        id,
                        new RefundService.Account(
                                request.bankName(),
                                request.accountNumber(),
                                request.accountName())));
    }

    @GetMapping("/orders/{id}")
    public OrderResponse order(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.order(jwt.getSubject(), id));
    }
}
