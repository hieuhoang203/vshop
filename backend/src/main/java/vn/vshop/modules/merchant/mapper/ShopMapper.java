package vn.vshop.modules.merchant.mapper;

import vn.vshop.modules.merchant.dto.response.ShopResponse;
import vn.vshop.modules.merchant.entity.Shop;

public final class ShopMapper {
    private ShopMapper() {}

    public static ShopResponse toResponse(Shop shop) {
        return new ShopResponse(
                shop.id(),
                shop.name(),
                shop.description(),
                shop.status(),
                shop.rejectionReason(),
                shop.createdAt());
    }
}
