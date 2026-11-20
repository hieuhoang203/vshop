package vn.vshop.modules.checkout.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.catalog.model.SkuOffer;
import vn.vshop.modules.catalog.service.CatalogService;
import vn.vshop.modules.checkout.entity.Cart;
import vn.vshop.modules.checkout.model.CartView;
import vn.vshop.modules.checkout.model.PricedCart;
import vn.vshop.modules.checkout.repository.CartRepository;
import vn.vshop.modules.identity.service.AccountService;

/**
 * Buyer cart commands. A cart stores intent (SKU and quantity) and never holds stock or a price.
 * Every mutation locks the cart row and bumps its version, which invalidates earlier quotes.
 */
@Service
@Transactional
public class CartService {
    static final int MAX_LINES = 50;
    static final int MAX_QUANTITY = 99;

    private final CartRepository carts;
    private final CartPricer pricer;
    private final CatalogService catalog;
    private final AccountService accounts;

    CartService(
            CartRepository carts,
            CartPricer pricer,
            CatalogService catalog,
            AccountService accounts) {
        this.carts = carts;
        this.pricer = pricer;
        this.catalog = catalog;
        this.accounts = accounts;
    }

    public CartView view(String subject) {
        UUID buyer = accounts.requireActiveAccount(subject);
        return carts.findByBuyer(buyer)
                .map(cart -> new CartView(cart.version(), pricer.price(carts.findLines(cart.id()))))
                .orElseGet(() -> new CartView(0, pricer.price(List.of())));
    }

    /** Adds quantity to the line (creating it); not idempotent by design. */
    public CartView addLine(String subject, UUID skuId, int quantity) {
        requireQuantity(quantity);
        Cart cart = carts.ensureAndLock(accounts.requireActiveAccount(subject));
        requireSaleable(skuId);
        requireRoom(cart, skuId);
        carts.addQuantity(cart.id(), skuId, quantity);
        return finish(cart);
    }

    /** Sets the line quantity; repeating the same request leaves the same result. */
    public CartView setQuantity(String subject, UUID skuId, int quantity) {
        requireQuantity(quantity);
        Cart cart = carts.ensureAndLock(accounts.requireActiveAccount(subject));
        requireSaleable(skuId);
        if (carts.findQuantity(cart.id(), skuId).filter(q -> q == quantity).isPresent()) {
            return current(cart); // nothing changes, so earlier quotes stay valid
        }
        requireRoom(cart, skuId);
        carts.setQuantity(cart.id(), skuId, quantity);
        return finish(cart);
    }

    public CartView removeLine(String subject, UUID skuId) {
        Cart cart = carts.ensureAndLock(accounts.requireActiveAccount(subject));
        return carts.deleteLine(cart.id(), skuId) ? finish(cart) : current(cart);
    }

    public CartView clear(String subject) {
        Cart cart = carts.ensureAndLock(accounts.requireActiveAccount(subject));
        return carts.deleteAllLines(cart.id()) > 0 ? finish(cart) : current(cart);
    }

    private CartView finish(Cart cart) {
        long version = carts.bumpVersion(cart.id());
        return new CartView(version, pricer.price(carts.findLines(cart.id())));
    }

    private CartView current(Cart cart) {
        PricedCart priced = pricer.price(carts.findLines(cart.id()));
        return new CartView(cart.version(), priced);
    }

    private void requireSaleable(UUID skuId) {
        SkuOffer offer =
                catalog.offers(List.of(skuId)).stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PRODUCT_NOT_FOUND,
                                                "Không tìm thấy sản phẩm."));
        if (!offer.saleable()) {
            throw new BusinessException(
                    ErrorCode.SKU_UNAVAILABLE, "Sản phẩm này hiện không còn được bán.");
        }
    }

    private void requireRoom(Cart cart, UUID skuId) {
        if (!carts.lineExists(cart.id(), skuId) && carts.countLines(cart.id()) >= MAX_LINES) {
            throw new BusinessException(
                    ErrorCode.INVALID_CART_INPUT,
                    "Giỏ hàng tối đa " + MAX_LINES + " sản phẩm khác nhau.");
        }
    }

    private static void requireQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BusinessException(
                    ErrorCode.INVALID_CART_INPUT, "Số lượng phải từ 1 đến " + MAX_QUANTITY + ".");
        }
    }
}
