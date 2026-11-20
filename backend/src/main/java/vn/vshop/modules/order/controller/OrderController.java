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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.order.dto.request.PlaceOrderRequest;
import vn.vshop.modules.order.dto.response.CheckoutGroupResponse;
import vn.vshop.modules.order.dto.response.OrderResponse;
import vn.vshop.modules.order.dto.response.OrderSummaryResponse;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.OrderService;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
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
            @Valid @RequestBody PlaceOrderRequest request) {
        var result =
                service.place(
                        jwt.getSubject(),
                        request.quoteId(),
                        request.paymentMethod(),
                        idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(OrderMapper.toResponse(result));
    }

    @GetMapping("/checkout/groups/{id}")
    public CheckoutGroupResponse group(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.group(jwt.getSubject(), id));
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

    @GetMapping("/orders/{id}")
    public OrderResponse order(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.order(jwt.getSubject(), id));
    }
}
