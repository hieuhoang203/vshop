package vn.vshop.modules.catalog.dto.response;

import java.util.UUID;

public record PublicSkuResponse(UUID id, String variantLabel, long price, long available) {}
