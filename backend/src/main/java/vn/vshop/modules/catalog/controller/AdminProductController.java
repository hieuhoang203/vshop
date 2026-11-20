package vn.vshop.modules.catalog.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import vn.vshop.modules.catalog.dto.request.ReviewProductRequest;
import vn.vshop.modules.catalog.dto.response.SellerProductResponse;
import vn.vshop.modules.catalog.mapper.SellerProductMapper;
import vn.vshop.modules.catalog.service.ProductModerationService;

/** ADMIN-only by the /api/v1/admin/** rule in the security configuration. */
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {
    private final ProductModerationService service;

    public AdminProductController(ProductModerationService service) {
        this.service = service;
    }

    @GetMapping("/review-queue")
    public PageResponse<SellerProductResponse> queue(
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return SellerProductMapper.toResponse(service.queue(page, size));
    }

    @PostMapping("/{productId}/review")
    public SellerProductResponse review(
            @PathVariable UUID productId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReviewProductRequest request) {
        return SellerProductMapper.toResponse(
                service.review(
                        jwt.getSubject(),
                        productId,
                        request.decision(),
                        request.reason(),
                        request.expectedVersion()));
    }
}
