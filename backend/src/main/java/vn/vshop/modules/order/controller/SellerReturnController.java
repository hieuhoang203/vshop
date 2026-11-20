package vn.vshop.modules.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
import vn.vshop.modules.order.dto.request.NoteRequest;
import vn.vshop.modules.order.dto.request.OfferRefundRequest;
import vn.vshop.modules.order.dto.request.ReasonRequest;
import vn.vshop.modules.order.dto.response.ReturnCaseResponse;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.ReturnCaseService;

/** The shop side of return cases (owner of the shop only). */
@RestController
@RequestMapping("/api/v1/seller/shops/{shopId}/return-cases")
public class SellerReturnController {
    private final ReturnCaseService service;

    public SellerReturnController(ReturnCaseService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ReturnCaseResponse> list(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z_]{3,20}") String status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        List<ReturnCaseResponse> items =
                service.listForShop(jwt.getSubject(), shopId, status, page, size).stream()
                        .map(OrderMapper::toResponse)
                        .toList();
        return new PageResponse<>(
                items, service.countForShop(jwt.getSubject(), shopId, status), page, size);
    }

    /** Offers money back without taking the goods back (all or part of the request). */
    @PostMapping("/{id}/offer-refund")
    public ReturnCaseResponse offer(
            @PathVariable UUID shopId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OfferRefundRequest request) {
        return OrderMapper.toResponse(
                service.offerRefund(
                        jwt.getSubject(), shopId, id, request.amount(), request.note()));
    }

    @PostMapping("/{id}/approve-return")
    public ReturnCaseResponse approveReturn(
            @PathVariable UUID shopId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NoteRequest request) {
        return OrderMapper.toResponse(
                service.approveReturn(jwt.getSubject(), shopId, id, request.note()));
    }

    @PostMapping("/{id}/reject")
    public ReturnCaseResponse reject(
            @PathVariable UUID shopId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest request) {
        return OrderMapper.toResponse(
                service.reject(jwt.getSubject(), shopId, id, request.reason()));
    }

    /** The returned goods arrived in good order: the buyer is refunded in full. */
    @PostMapping("/{id}/confirm-received")
    public ReturnCaseResponse confirmReceived(
            @PathVariable UUID shopId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmReceivedRequest request) {
        return OrderMapper.toResponse(
                service.confirmReceived(
                        jwt.getSubject(), shopId, id, request.restock(), request.note()));
    }

    @PostMapping("/{id}/inspection-failed")
    public ReturnCaseResponse inspectionFailed(
            @PathVariable UUID shopId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest request) {
        return OrderMapper.toResponse(
                service.reportInspectionFailed(jwt.getSubject(), shopId, id, request.reason()));
    }
}
