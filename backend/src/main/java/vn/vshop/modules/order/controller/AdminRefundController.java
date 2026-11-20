package vn.vshop.modules.order.controller;

import jakarta.validation.Valid;
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
import vn.vshop.modules.order.dto.request.ReasonRequest;
import vn.vshop.modules.order.dto.request.RefundPaidRequest;
import vn.vshop.modules.order.dto.response.AdminRefundResponse;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.RefundService;

/** Queue of refunds to pay out (administrators with a second factor only, enforced by the path). */
@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {
    private final RefundService service;

    public AdminRefundController(RefundService service) {
        this.service = service;
    }

    /** {@code status} is optional: AWAITING_ACCOUNT, APPROVED (ready to pay), SUCCEEDED. */
    @GetMapping
    public List<AdminRefundResponse> list(
            @RequestParam(required = false)
                    @jakarta.validation.constraints.Pattern(regexp = "[A-Z_]{3,20}")
                    String status) {
        return service.adminList(status).stream().map(OrderMapper::toResponse).toList();
    }

    /** Records that the transfer was made; once only. */
    @PostMapping("/{id}/paid")
    public AdminRefundResponse paid(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RefundPaidRequest request) {
        return OrderMapper.toResponse(
                service.markPaid(jwt.getSubject(), id, request.payoutReference(), request.note()));
    }

    /** The account was wrong: the buyer is asked to enter it again. */
    @PostMapping("/{id}/bad-account")
    public AdminRefundResponse badAccount(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest request) {
        return OrderMapper.toResponse(service.badAccount(jwt.getSubject(), id, request.reason()));
    }
}
