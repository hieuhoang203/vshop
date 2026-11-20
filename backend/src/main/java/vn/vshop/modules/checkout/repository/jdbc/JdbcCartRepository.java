package vn.vshop.modules.checkout.repository.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.checkout.entity.Cart;
import vn.vshop.modules.checkout.entity.CartLine;
import vn.vshop.modules.checkout.repository.CartRepository;

@Repository
public class JdbcCartRepository implements CartRepository {
    private static final String CART_COLUMNS =
            "id, buyer_account_id, version, created_at, updated_at";
    private static final int MAX_LINE_QUANTITY = 99;
    private final JdbcClient jdbc;

    public JdbcCartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Cart ensureAndLock(UUID buyerAccountId) {
        jdbc.sql(
                        """
            INSERT INTO checkout.cart (id, buyer_account_id) VALUES (:id, :buyer)
            ON CONFLICT (buyer_account_id) DO NOTHING
            """)
                .param("id", UUID.randomUUID())
                .param("buyer", buyerAccountId)
                .update();
        return jdbc.sql(
                        "SELECT "
                                + CART_COLUMNS
                                + " FROM checkout.cart WHERE buyer_account_id = :buyer FOR UPDATE")
                .param("buyer", buyerAccountId)
                .query(Cart.class)
                .single();
    }

    @Override
    public Optional<Cart> findByBuyer(UUID buyerAccountId) {
        return jdbc.sql(
                        "SELECT "
                                + CART_COLUMNS
                                + " FROM checkout.cart WHERE buyer_account_id = :buyer")
                .param("buyer", buyerAccountId)
                .query(Cart.class)
                .optional();
    }

    @Override
    public List<CartLine> findLines(UUID cartId) {
        return jdbc.sql(
                        """
            SELECT cart_id, sku_id, quantity, created_at, updated_at FROM checkout.cart_line
            WHERE cart_id = :cart ORDER BY created_at, sku_id
            """)
                .param("cart", cartId)
                .query(CartLine.class)
                .list();
    }

    @Override
    public long countLines(UUID cartId) {
        return jdbc.sql("SELECT count(*) FROM checkout.cart_line WHERE cart_id = :cart")
                .param("cart", cartId)
                .query(Long.class)
                .single();
    }

    @Override
    public boolean lineExists(UUID cartId, UUID skuId) {
        return jdbc.sql(
                                "SELECT count(*) FROM checkout.cart_line WHERE cart_id = :cart AND sku_id = :sku")
                        .param("cart", cartId)
                        .param("sku", skuId)
                        .query(Long.class)
                        .single()
                == 1;
    }

    @Override
    public Optional<Integer> findQuantity(UUID cartId, UUID skuId) {
        return jdbc.sql(
                        "SELECT quantity FROM checkout.cart_line WHERE cart_id = :cart AND sku_id = :sku")
                .param("cart", cartId)
                .param("sku", skuId)
                .query(Integer.class)
                .optional();
    }

    @Override
    public void addQuantity(UUID cartId, UUID skuId, int quantity) {
        jdbc.sql(
                        """
            INSERT INTO checkout.cart_line (cart_id, sku_id, quantity, created_at)
            VALUES (:cart, :sku, LEAST(:quantity, :max), clock_timestamp())
            ON CONFLICT (cart_id, sku_id) DO UPDATE
               SET quantity = LEAST(checkout.cart_line.quantity + :quantity, :max), updated_at = now()
            """)
                .param("cart", cartId)
                .param("sku", skuId)
                .param("quantity", quantity)
                .param("max", MAX_LINE_QUANTITY)
                .update();
    }

    @Override
    public void setQuantity(UUID cartId, UUID skuId, int quantity) {
        jdbc.sql(
                        """
            INSERT INTO checkout.cart_line (cart_id, sku_id, quantity, created_at)
            VALUES (:cart, :sku, :quantity, clock_timestamp())
            ON CONFLICT (cart_id, sku_id) DO UPDATE SET quantity = :quantity, updated_at = now()
            """)
                .param("cart", cartId)
                .param("sku", skuId)
                .param("quantity", quantity)
                .update();
    }

    @Override
    public boolean deleteLine(UUID cartId, UUID skuId) {
        return jdbc.sql("DELETE FROM checkout.cart_line WHERE cart_id = :cart AND sku_id = :sku")
                        .param("cart", cartId)
                        .param("sku", skuId)
                        .update()
                == 1;
    }

    @Override
    public int deleteAllLines(UUID cartId) {
        return jdbc.sql("DELETE FROM checkout.cart_line WHERE cart_id = :cart")
                .param("cart", cartId)
                .update();
    }

    @Override
    public long bumpVersion(UUID cartId) {
        return jdbc.sql(
                        "UPDATE checkout.cart SET version = version + 1, updated_at = now() WHERE id = :id RETURNING version")
                .param("id", cartId)
                .query(Long.class)
                .single();
    }
}
