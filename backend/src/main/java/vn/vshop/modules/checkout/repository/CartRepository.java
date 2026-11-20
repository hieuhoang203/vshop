package vn.vshop.modules.checkout.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.checkout.entity.Cart;
import vn.vshop.modules.checkout.entity.CartLine;

/** Every mutation first locks the cart row, so cart version and lines change together. */
public interface CartRepository {
    Cart ensureAndLock(UUID buyerAccountId);

    Optional<Cart> findByBuyer(UUID buyerAccountId);

    List<CartLine> findLines(UUID cartId);

    long countLines(UUID cartId);

    boolean lineExists(UUID cartId, UUID skuId);

    Optional<Integer> findQuantity(UUID cartId, UUID skuId);

    /** Adds to the line quantity, capped at the maximum per line. */
    void addQuantity(UUID cartId, UUID skuId, int quantity);

    void setQuantity(UUID cartId, UUID skuId, int quantity);

    boolean deleteLine(UUID cartId, UUID skuId);

    int deleteAllLines(UUID cartId);

    /** Returns the new version. */
    long bumpVersion(UUID cartId);
}
