package vn.vshop.modules.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import vn.vshop.modules.catalog.entity.ProductStatus;

public record ChangeProductStatusRequest(
        @NotNull ProductStatus status, @NotNull @Min(0) Long expectedVersion) {}
