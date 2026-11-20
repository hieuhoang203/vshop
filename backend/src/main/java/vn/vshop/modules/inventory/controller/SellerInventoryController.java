package vn.vshop.modules.inventory.controller;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.inventory.dto.request.StockAdjustmentRequest;
import vn.vshop.modules.inventory.dto.response.StockBalanceResponse;
import vn.vshop.modules.inventory.dto.response.StockMovementResponse;
import vn.vshop.modules.inventory.mapper.InventoryMapper;
import vn.vshop.modules.inventory.service.StockService;

@RestController
@RequestMapping("/api/v1/seller/shops/{shopId}/inventory")
public class SellerInventoryController {
    private final StockService service;

    public SellerInventoryController(StockService service) {
        this.service = service;
    }

    /** Idempotent: retrying with the same Idempotency-Key and body applies the change once. */
    @PostMapping("/adjustments")
    public StockBalanceResponse adjust(
            @PathVariable UUID shopId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}")
                    String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return InventoryMapper.toResponse(
                service.adjust(
                        jwt.getSubject(),
                        shopId,
                        InventoryMapper.toCommand(request, idempotencyKey)));
    }

    @GetMapping("/skus/{skuId}")
    public List<StockBalanceResponse> balances(
            @PathVariable UUID shopId, @PathVariable UUID skuId, @AuthenticationPrincipal Jwt jwt) {
        return service.balances(jwt.getSubject(), shopId, skuId).stream()
                .map(InventoryMapper::toResponse)
                .toList();
    }

    @GetMapping("/skus/{skuId}/movements")
    public List<StockMovementResponse> movements(
            @PathVariable UUID shopId,
            @PathVariable UUID skuId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @AuthenticationPrincipal Jwt jwt) {
        return service.movements(jwt.getSubject(), shopId, skuId, limit).stream()
                .map(InventoryMapper::toResponse)
                .toList();
    }
}
