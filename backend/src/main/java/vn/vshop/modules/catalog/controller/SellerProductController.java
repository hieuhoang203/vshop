package vn.vshop.modules.catalog.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.catalog.dto.request.ChangeProductStatusRequest;
import vn.vshop.modules.catalog.dto.request.CreateProductRequest;
import vn.vshop.modules.catalog.dto.request.UpdateProductRequest;
import vn.vshop.modules.catalog.dto.request.UpdateSkuRequest;
import vn.vshop.modules.catalog.dto.response.SellerProductResponse;
import vn.vshop.modules.catalog.mapper.SellerProductMapper;
import vn.vshop.modules.catalog.service.SellerCatalogService;

@RestController
@RequestMapping("/api/v1/seller/shops/{shopId}/products")
public class SellerProductController {
    private final SellerCatalogService service;

    public SellerProductController(SellerCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SellerProductResponse> list(
            @PathVariable UUID shopId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return SellerProductMapper.toResponse(service.list(jwt.getSubject(), shopId, page, size));
    }

    @GetMapping("/{productId}")
    public SellerProductResponse detail(
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal Jwt jwt) {
        return SellerProductMapper.toResponse(service.get(jwt.getSubject(), shopId, productId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SellerProductResponse create(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProductRequest request) {
        return SellerProductMapper.toResponse(
                service.create(jwt.getSubject(), shopId, SellerProductMapper.toCommand(request)));
    }

    @PutMapping("/{productId}")
    public SellerProductResponse update(
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProductRequest request) {
        return SellerProductMapper.toResponse(
                service.updateContent(
                        jwt.getSubject(),
                        shopId,
                        productId,
                        SellerProductMapper.toCommand(request)));
    }

    @PutMapping("/{productId}/status")
    public SellerProductResponse changeStatus(
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeProductStatusRequest request) {
        return SellerProductMapper.toResponse(
                service.changeStatus(
                        jwt.getSubject(),
                        shopId,
                        productId,
                        request.status(),
                        request.expectedVersion()));
    }

    @PostMapping("/{productId}/skus")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerProductResponse addSku(
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProductRequest.SkuRequest request) {
        return SellerProductMapper.toResponse(
                service.addSku(
                        jwt.getSubject(),
                        shopId,
                        productId,
                        SellerProductMapper.toCommand(request)));
    }

    @PutMapping("/{productId}/skus/{skuId}")
    public SellerProductResponse updateSku(
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @PathVariable UUID skuId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSkuRequest request) {
        return SellerProductMapper.toResponse(
                service.updateSku(
                        jwt.getSubject(),
                        shopId,
                        productId,
                        skuId,
                        SellerProductMapper.toCommand(request)));
    }
}
