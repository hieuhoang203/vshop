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
import vn.vshop.modules.order.dto.request.CodSettledRequest;
import vn.vshop.modules.order.dto.response.CodReceivableResponse;
import vn.vshop.modules.order.entity.CodReceivable;
import vn.vshop.modules.order.service.CodLedgerService;

/** Cash-on-delivery money held by carriers (administrators with a second factor only). */
@RestController
@RequestMapping("/api/v1/admin/cod-receivables")
public class AdminCodController {
    private final CodLedgerService service;

    public AdminCodController(CodLedgerService service) {
        this.service = service;
    }

    /** {@code status}: WITH_CARRIER (not remitted yet) or SETTLED; empty lists both. */
    @GetMapping
    public List<CodReceivableResponse> list(
            @RequestParam(required = false)
                    @jakarta.validation.constraints.Pattern(regexp = "WITH_CARRIER|SETTLED")
                    String status) {
        return service.list(status).stream().map(AdminCodController::toResponse).toList();
    }

    /** Records that the carrier remitted the cash; once only. */
    @PostMapping("/{id}/settled")
    public CodReceivableResponse settled(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CodSettledRequest request) {
        return toResponse(service.settle(jwt.getSubject(), id, request.reference()));
    }

    private static CodReceivableResponse toResponse(CodReceivable c) {
        return new CodReceivableResponse(
                c.id(),
                c.sellerOrderId(),
                c.orderNumber(),
                c.amount(),
                c.refundedAmount(),
                c.amount() - c.refundedAmount(),
                c.collectedBy(),
                c.status(),
                c.settledReference(),
                c.settledAt(),
                c.createdAt());
    }
}
