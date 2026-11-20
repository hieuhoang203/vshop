package vn.vshop.modules.checkout.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.vshop.modules.catalog.model.SkuOffer;
import vn.vshop.modules.catalog.service.CatalogService;
import vn.vshop.modules.checkout.entity.CartLine;
import vn.vshop.modules.checkout.model.CartLineIssue;
import vn.vshop.modules.checkout.model.PricedCart;
import vn.vshop.modules.inventory.service.StockQueryService;
import vn.vshop.modules.platform.service.PolicyService;

/**
 * Prices cart lines from the catalog and stock as they are now. Prices are never taken from the
 * client or stored in the cart. Money is exact integer VND; products are multiplied with overflow
 * checks. Stock is a read, not a hold (BR03).
 */
@Component
class CartPricer {
    private final CatalogService catalog;
    private final StockQueryService stock;
    private final PolicyService policies;

    CartPricer(CatalogService catalog, StockQueryService stock, PolicyService policies) {
        this.catalog = catalog;
        this.stock = stock;
        this.policies = policies;
    }

    PricedCart price(List<CartLine> lines) {
        List<UUID> skuIds = lines.stream().map(CartLine::skuId).toList();
        Map<UUID, SkuOffer> offers =
                catalog.offers(skuIds).stream()
                        .collect(Collectors.toMap(SkuOffer::skuId, Function.identity()));
        Map<UUID, Long> available = stock.available(skuIds);

        Map<UUID, List<PricedCart.Line>> byShop = new LinkedHashMap<>();
        Map<UUID, String> shopNames = new LinkedHashMap<>();
        int itemCount = 0;
        boolean allBuyable = !lines.isEmpty();
        for (CartLine line : lines) {
            SkuOffer offer = offers.get(line.skuId());
            if (offer == null) {
                continue; // cannot happen with the FK, but never fabricate a priced line
            }
            long sellable = available.getOrDefault(line.skuId(), 0L);
            CartLineIssue issue = null;
            if (!offer.saleable()) {
                issue = CartLineIssue.UNAVAILABLE;
                sellable = 0;
            } else if (sellable <= 0) {
                issue = CartLineIssue.OUT_OF_STOCK;
            } else if (sellable < line.quantity()) {
                issue = CartLineIssue.INSUFFICIENT_STOCK;
            }
            allBuyable &= issue == null;
            itemCount += line.quantity();
            byShop.computeIfAbsent(offer.shopId(), id -> new ArrayList<>())
                    .add(
                            new PricedCart.Line(
                                    offer.skuId(),
                                    offer.productId(),
                                    offer.productSlug(),
                                    offer.productName(),
                                    offer.imageKey(),
                                    offer.variantLabel(),
                                    offer.sellerSku(),
                                    offer.price(),
                                    line.quantity(),
                                    Math.multiplyExact(offer.price(), (long) line.quantity()),
                                    issue,
                                    Math.max(sellable, 0)));
            shopNames.putIfAbsent(offer.shopId(), offer.shopName());
        }

        var shipping = policies.checkoutPolicies().shipping();
        long fee = shipping == null ? 0 : shipping.amount();
        List<PricedCart.ShopGroup> groups = new ArrayList<>();
        long itemsTotal = 0;
        long shippingTotal = 0;
        for (var entry : byShop.entrySet()) {
            long subtotal = 0;
            for (PricedCart.Line line : entry.getValue()) {
                if (line.issue() == null) {
                    subtotal = Math.addExact(subtotal, line.lineTotal());
                }
            }
            long groupFee = subtotal > 0 ? fee : 0;
            itemsTotal = Math.addExact(itemsTotal, subtotal);
            shippingTotal = Math.addExact(shippingTotal, groupFee);
            groups.add(
                    new PricedCart.ShopGroup(
                            entry.getKey(),
                            shopNames.get(entry.getKey()),
                            entry.getValue(),
                            subtotal,
                            groupFee,
                            Math.addExact(subtotal, groupFee)));
        }
        return new PricedCart(
                groups,
                itemCount,
                itemsTotal,
                shippingTotal,
                Math.addExact(itemsTotal, shippingTotal),
                shipping != null,
                allBuyable && !groups.isEmpty());
    }
}
