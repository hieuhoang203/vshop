package vn.vshop.modules.catalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import vn.vshop.modules.catalog.entity.SkuStatus;

public record UpdateSkuRequest(
        @NotNull @Min(1) @Max(1_000_000_000L) Long price,
        @NotNull SkuStatus status,
        @NotNull @Min(0) Long expectedVersion) {}
