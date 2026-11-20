package vn.vshop.modules.catalog.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SellerProductResponse(
        UUID id,
        UUID shopId,
        UUID categoryId,
        String slug,
        String name,
        String description,
        String imageKey,
        String status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OptionResponse> options,
        List<SkuResponse> skus) {
    public record OptionResponse(String code, String name, List<ValueResponse> values) {}

    public record ValueResponse(String code, String label) {}

    public record SkuResponse(
            UUID id,
            String sellerSku,
            long price,
            String currency,
            String status,
            long version,
            Map<String, String> optionValues,
            long available) {}
}
