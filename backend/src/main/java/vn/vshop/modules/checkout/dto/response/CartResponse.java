package vn.vshop.modules.checkout.dto.response;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vn.vshop.modules.checkout.model.CartLineIssue;

/**
 * Cart or quote content. Amounts are integer VND. {@code notCalculated} lists price components that
 * do not exist yet (shipping, discounts, tax), so a client never presents {@code itemsTotal} as a
 * final payable amount.
 */
public record CartResponse(
        long version,
        int itemCount,
        String currency,
        List<ShopGroupResponse> groups,
        long itemsTotal,
        long shippingTotal,
        long payableTotal,
        boolean canCheckout,
        List<String> notCalculated) {
    public record ShopGroupResponse(
            UUID shopId,
            String shopName,
            long subtotal,
            long shippingFee,
            long payable,
            List<LineResponse> lines) {}

    public record LineResponse(
            UUID skuId,
            UUID productId,
            String productSlug,
            String productName,
            String imageKey,
            @Nullable String variantLabel,
            String sellerSku,
            long unitPrice,
            int quantity,
            long lineTotal,
            @Nullable CartLineIssue issue,
            long availableQuantity) {}
}
