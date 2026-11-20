package vn.vshop.modules.checkout.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.checkout.entity.Cart;
import vn.vshop.modules.checkout.entity.Quote;
import vn.vshop.modules.checkout.model.OrderBasis;
import vn.vshop.modules.checkout.model.PricedCart;
import vn.vshop.modules.checkout.model.QuoteView;
import vn.vshop.modules.checkout.repository.CartRepository;
import vn.vshop.modules.checkout.repository.QuoteRepository;
import vn.vshop.modules.identity.model.ShipTo;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.identity.service.AddressService;

/**
 * Server-side quotes (BR04). A quote is calculated from the buyer's own cart, stored as an
 * immutable snapshot with an expiry and the cart version it was built from, so a later cart change
 * or expiry makes it not {@code current}. Shipping, vouchers and tax are not calculated yet.
 */
@Service
@Transactional
public class QuoteService {
    private final CartRepository carts;
    private final QuoteRepository quotes;
    private final CartPricer pricer;
    private final AccountService accounts;
    private final AddressService addresses;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration ttl;

    QuoteService(
            CartRepository carts,
            QuoteRepository quotes,
            CartPricer pricer,
            AccountService accounts,
            AddressService addresses,
            ObjectMapper json,
            Clock clock,
            @Value("${vshop.checkout.quote-ttl:PT10M}") Duration ttl) {
        this.carts = carts;
        this.quotes = quotes;
        this.pricer = pricer;
        this.accounts = accounts;
        this.addresses = addresses;
        this.json = json;
        this.clock = clock;
        this.ttl = ttl;
    }

    /** {@code addressId} is optional; when given it must belong to the caller. */
    public QuoteView create(String subject, UUID addressId) {
        UUID buyer = accounts.requireActiveAccount(subject);
        ShipTo shipTo = addressId == null ? null : addresses.shipTo(subject, addressId);
        // Lock the cart so the version we record matches the lines we price.
        Cart cart = carts.ensureAndLock(buyer);
        var lines = carts.findLines(cart.id());
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY, "Giỏ hàng đang trống.");
        }
        PricedCart priced = pricer.price(lines);
        UUID id = UUID.randomUUID();
        quotes.insert(
                new Quote(
                        id,
                        buyer,
                        cart.id(),
                        cart.version(),
                        priced.payableTotal(),
                        "VND",
                        json.writeValueAsString(priced),
                        null,
                        null,
                        shipTo == null ? null : shipTo.addressId(),
                        shipTo == null ? null : shipTo.version(),
                        shipTo == null ? null : shipTo.province(),
                        shipTo == null ? null : shipTo.ward()),
                ttl.toSeconds());
        return view(subject, quotes.findForBuyer(id, buyer).orElseThrow(), cart.version());
    }

    /**
     * First step of placing an order: locks the caller's cart row for the rest of the transaction,
     * the same lock every cart change takes, and returns the buyer account.
     */
    public UUID lockCart(String subject) {
        UUID buyer = accounts.requireActiveAccount(subject);
        carts.ensureAndLock(buyer);
        return buyer;
    }

    /**
     * Validates a quote for ordering and prices the cart again. Must run in the transaction that
     * called {@link #lockCart}. Anything that differs from what the buyer was shown (expiry, cart
     * or address change, price, shipping, stock) is a {@code QUOTE_STALE} conflict; an order is
     * never placed at a price the buyer has not seen (BR04).
     */
    public OrderBasis prepareOrder(String subject, UUID quoteId) {
        QuoteView quote = get(subject, quoteId);
        if (!quote.current()) {
            throw new BusinessException(
                    ErrorCode.QUOTE_STALE,
                    quote.expired()
                            ? "Báo giá đã hết hạn. Vui lòng tính lại trước khi đặt hàng."
                            : "Giỏ hàng hoặc địa chỉ đã thay đổi. Vui lòng tính lại báo giá.");
        }
        if (quote.shipTo() == null || quote.shipTo().addressId() == null) {
            throw new BusinessException(
                    ErrorCode.QUOTE_ADDRESS_REQUIRED, "Cần chọn địa chỉ giao hàng để đặt hàng.");
        }
        UUID buyer = accounts.requireActiveAccount(subject);
        Cart cart = carts.ensureAndLock(buyer);
        PricedCart priced = pricer.price(carts.findLines(cart.id()));
        if (!priced.canCheckout()) {
            throw new BusinessException(
                    ErrorCode.SKU_UNAVAILABLE,
                    "Có sản phẩm không còn đủ hàng hoặc đã ngừng bán. Hãy kiểm tra lại giỏ hàng.");
        }
        if (!sameCharges(quote.cart(), priced)) {
            throw new BusinessException(
                    ErrorCode.QUOTE_STALE,
                    "Giá đã thay đổi: báo giá "
                            + quote.cart().payableTotal()
                            + "đ, hiện tại "
                            + priced.payableTotal()
                            + "đ. Vui lòng xem lại báo giá mới.");
        }
        return new OrderBasis(buyer, cart.id(), quote.id(), quote.shipTo().addressId(), priced);
    }

    /** Removes the purchased lines after an order was placed and bumps the cart version. */
    public void clearCart(UUID cartId) {
        carts.deleteAllLines(cartId);
        carts.bumpVersion(cartId);
    }

    /** Same shops, unit prices, quantities and fees; stock counts may differ harmlessly. */
    private static boolean sameCharges(PricedCart shown, PricedCart now) {
        if (shown.payableTotal() != now.payableTotal()
                || shown.shippingTotal() != now.shippingTotal()
                || shown.groups().size() != now.groups().size()) {
            return false;
        }
        for (int i = 0; i < shown.groups().size(); i++) {
            var a = shown.groups().get(i);
            var b = now.groups().get(i);
            if (!a.shopId().equals(b.shopId())
                    || a.subtotal() != b.subtotal()
                    || a.shippingFee() != b.shippingFee()
                    || a.lines().size() != b.lines().size()) {
                return false;
            }
            for (int j = 0; j < a.lines().size(); j++) {
                var x = a.lines().get(j);
                var y = b.lines().get(j);
                if (!x.skuId().equals(y.skuId())
                        || x.unitPrice() != y.unitPrice()
                        || x.quantity() != y.quantity()) {
                    return false;
                }
            }
        }
        return true;
    }

    public QuoteView get(String subject, UUID quoteId) {
        UUID buyer = accounts.requireActiveAccount(subject);
        Quote quote =
                quotes.findForBuyer(quoteId, buyer)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.QUOTE_NOT_FOUND,
                                                "Không tìm thấy báo giá."));
        long currentVersion = carts.findByBuyer(buyer).map(Cart::version).orElse(-1L);
        return view(subject, quote, currentVersion);
    }

    private QuoteView view(String subject, Quote quote, long currentCartVersion) {
        boolean expired = !quote.expiresAt().isAfter(OffsetDateTime.now(clock));
        // A quote bound to an address is stale once that address changed or was deleted (BR04).
        boolean addressUnchanged =
                quote.addressVersion() == null
                        || (quote.addressId() != null
                                && addresses
                                        .currentVersion(subject, quote.addressId())
                                        .filter(v -> v == quote.addressVersion().longValue())
                                        .isPresent());
        PricedCart priced = json.readValue(quote.snapshot(), PricedCart.class);
        return new QuoteView(
                quote.id(),
                quote.cartVersion(),
                quote.createdAt(),
                quote.expiresAt(),
                expired,
                !expired && addressUnchanged && quote.cartVersion() == currentCartVersion,
                priced,
                quote.addressVersion() == null
                        ? null
                        : new QuoteView.ShipTo(
                                quote.addressId(), quote.shipProvince(), quote.shipWard()));
    }
}
