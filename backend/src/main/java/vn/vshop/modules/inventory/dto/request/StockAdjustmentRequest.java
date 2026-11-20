package vn.vshop.modules.inventory.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StockAdjustmentRequest(
        @NotNull UUID skuId,
        @NotNull StockAdjustmentType type,
        @NotNull @Min(-1_000_000) @Max(1_000_000) Long delta,
        @NotBlank @Size(min = 5, max = 500) String reason) {
    public StockAdjustmentRequest {
        if (reason != null) reason = reason.strip();
    }

    /** Only these movement kinds are available to sellers. */
    public enum StockAdjustmentType {
        RECEIPT,
        ADJUSTMENT
    }
}
