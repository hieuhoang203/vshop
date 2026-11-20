package vn.vshop.modules.checkout.model;

import java.util.List;
import java.util.UUID;

/**
 * A cart priced from current catalog data and stock. {@code subtotal} and {@code itemsTotal} count
 * only lines without an issue. Shipping, discounts and tax are not calculated yet and are not part
 * of any amount here.
 */
public record PricedCart(
        List<ShopGroup> groups,
        int itemCount,
        long itemsTotal,
        long shippingTotal,
        long payableTotal,
        boolean shippingCalculated,
        boolean canCheckout) {
    public PricedCart {
        groups = List.copyOf(groups);
    }

    public record ShopGroup(
            UUID shopId,
            String shopName,
            List<Line> lines,
            long subtotal,
            long shippingFee,
            long payable) {
        public ShopGroup {
            lines = List.copyOf(lines);
        }
    }

    public record Line(
            UUID skuId,
            UUID productId,
            String productSlug,
            String productName,
            String imageKey,
            String variantLabel,
            String sellerSku,
            long unitPrice,
            int quantity,
            long lineTotal,
            CartLineIssue issue,
            long availableQuantity) {}
}
