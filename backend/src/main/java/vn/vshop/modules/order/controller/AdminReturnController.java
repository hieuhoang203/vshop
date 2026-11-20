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
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.order.dto.request.DecideReturnRequest;
import vn.vshop.modules.order.dto.response.ReturnCaseResponse;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.ReturnCaseService;

/** Cases waiting for an administrator (administrators with a second factor only). */
@RestController
@RequestMapping("/api/v1/admin/return-cases")
public class AdminReturnController {
    private final ReturnCaseService service;

    public AdminReturnController(ReturnCaseService service) {
        this.service = service;
    }

    /** REVIEWING cases, and REQUESTED ones the shop did not answer in time. */
    @GetMapping
    public List<ReturnCaseResponse> queue() {
        return service.adminQueue().stream().map(OrderMapper::toResponse).toList();
    }

    @PostMapping("/{id}/decide")
    public ReturnCaseResponse decide(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DecideReturnRequest request) {
        return OrderMapper.toResponse(
                service.decide(
                        jwt.getSubject(),
                        id,
                        request.decision(),
                        request.amount(),
                        request.note()));
    }
}
