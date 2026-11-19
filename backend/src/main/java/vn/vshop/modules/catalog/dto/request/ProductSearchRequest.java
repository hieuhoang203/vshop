package vn.vshop.modules.catalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductSearchRequest(
        @Size(max = 100) String q,
        @Size(max = 100) String category,
        @Min(0) @Max(10000) Integer page,
        @Min(1) @Max(48) Integer size,
        @Min(0) Long minPrice,
        @Min(0) Long maxPrice,
        @Pattern(regexp = "newest|price-asc|price-desc") String sort) {
    public ProductSearchRequest {
        q = q == null ? "" : q.strip();
        category = category == null ? "" : category.strip();
        page = page == null ? 0 : page;
        size = size == null ? 12 : size;
        minPrice = minPrice == null ? 0 : minPrice;
        maxPrice = maxPrice == null ? Long.MAX_VALUE : maxPrice;
        sort = sort == null ? "newest" : sort;
    }
}
