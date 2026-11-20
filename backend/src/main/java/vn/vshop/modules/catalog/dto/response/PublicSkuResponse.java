package vn.vshop.modules.catalog.dto.response;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PublicSkuResponse(
        UUID id, @Nullable String variantLabel, long price, long available) {}
