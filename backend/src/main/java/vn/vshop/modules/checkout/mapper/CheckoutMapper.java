package vn.vshop.modules.checkout.mapper;

import java.util.List;
import vn.vshop.modules.checkout.dto.response.CartResponse;
import vn.vshop.modules.checkout.dto.response.QuoteResponse;
import vn.vshop.modules.checkout.model.CartView;
import vn.vshop.modules.checkout.model.PricedCart;
import vn.vshop.modules.checkout.model.QuoteView;

public final class CheckoutMapper {
    private static final List<String> NOT_CALCULATED = List.of("SHIPPING", "DISCOUNT", "TAX");

    private CheckoutMapper() {}

    public static CartResponse toResponse(CartView view) {
        return toResponse(view.version(), view.cart());
    }

    public static QuoteResponse toResponse(QuoteView quote) {
        return new QuoteResponse(
                quote.id(),
                quote.createdAt(),
                quote.expiresAt(),
                quote.expired(),
                quote.current(),
                toResponse(quote.cartVersion(), quote.cart()),
                quote.shipTo() == null
                        ? null
                        : new QuoteResponse.ShipToResponse(
                                quote.shipTo().addressId(),
                                quote.shipTo().province(),
                                quote.shipTo().ward()));
    }

    private static CartResponse toResponse(long version, PricedCart cart) {
        return new CartResponse(
                version,
                cart.itemCount(),
                "VND",
                cart.groups().stream().map(CheckoutMapper::toResponse).toList(),
                cart.itemsTotal(),
                cart.canCheckout(),
                NOT_CALCULATED);
    }

    private static CartResponse.ShopGroupResponse toResponse(PricedCart.ShopGroup group) {
        return new CartResponse.ShopGroupResponse(
                group.shopId(),
                group.shopName(),
                group.subtotal(),
                group.lines().stream().map(CheckoutMapper::toResponse).toList());
    }

    private static CartResponse.LineResponse toResponse(PricedCart.Line line) {
        return new CartResponse.LineResponse(
                line.skuId(),
                line.productId(),
                line.productSlug(),
                line.productName(),
                line.imageKey(),
                line.variantLabel(),
                line.sellerSku(),
                line.unitPrice(),
                line.quantity(),
                line.lineTotal(),
                line.issue(),
                line.availableQuantity());
    }
}
