package vn.vshop.modules.order.repository.jdbc;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.order.entity.CheckoutGroup;
import vn.vshop.modules.order.entity.OrderLine;
import vn.vshop.modules.order.entity.OrderStatus;
import vn.vshop.modules.order.entity.OrderStatusEvent;
import vn.vshop.modules.order.entity.SellerOrder;
import vn.vshop.modules.order.model.OrderSummary;
import vn.vshop.modules.order.repository.OrderRepository;

@Repository
public class JdbcOrderRepository implements OrderRepository {
    private static final String GROUP_COLUMNS =
            """
            id, buyer_account_id, quote_id, idempotency_key, request_hash, currency, total_amount,
            payment_method, policy_snapshot::text AS policy_snapshot, created_at
            """;
    private static final String ORDER_COLUMNS =
            """
            o.id, o.checkout_group_id, o.shop_id, o.order_number, o.status, o.version, o.item_amount,
            o.discount_amount, o.shipping_amount, o.payable_amount, o.shop_name_snapshot,
            o.recipient_snapshot_ciphertext, o.ship_province, o.ship_ward, o.return_policy_id,
            o.payment_deadline, o.confirmed_at, o.canceled_at, o.cancel_reason, o.delivered_at,
            o.return_eligible_until, o.created_at, o.updated_at, o.manual_tracking_code, o.shipped_at
            """;

    private final JdbcClient jdbc;

    public JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CheckoutGroup> findGroupByKey(UUID buyer, String idempotencyKey) {
        return jdbc.sql(
                        "SELECT "
                                + GROUP_COLUMNS
                                + " FROM sales.checkout_group"
                                + " WHERE buyer_account_id = :buyer AND idempotency_key = :key")
                .param("buyer", buyer)
                .param("key", idempotencyKey)
                .query(CheckoutGroup.class)
                .optional();
    }

    @Override
    public Optional<CheckoutGroup> findGroupByQuote(UUID buyer, UUID quoteId) {
        return jdbc.sql(
                        "SELECT "
                                + GROUP_COLUMNS
                                + " FROM sales.checkout_group"
                                + " WHERE buyer_account_id = :buyer AND quote_id = :quote")
                .param("buyer", buyer)
                .param("quote", quoteId)
                .query(CheckoutGroup.class)
                .optional();
    }

    @Override
    public Optional<CheckoutGroup> findGroup(UUID buyer, UUID groupId) {
        return jdbc.sql(
                        "SELECT "
                                + GROUP_COLUMNS
                                + " FROM sales.checkout_group"
                                + " WHERE buyer_account_id = :buyer AND id = :id")
                .param("buyer", buyer)
                .param("id", groupId)
                .query(CheckoutGroup.class)
                .optional();
    }

    @Override
    public void insertGroup(CheckoutGroup g) {
        jdbc.sql(
                        """
            INSERT INTO sales.checkout_group
                (id, buyer_account_id, quote_id, idempotency_key, request_hash, currency,
                 total_amount, payment_method, policy_snapshot)
            VALUES (:id, :buyer, :quote, :key, :hash, :currency, :total, :method, CAST(:policy AS jsonb))
            """)
                .param("id", g.id())
                .param("buyer", g.buyerAccountId())
                .param("quote", g.quoteId())
                .param("key", g.idempotencyKey())
                .param("hash", g.requestHash())
                .param("currency", g.currency())
                .param("total", g.totalAmount())
                .param("method", g.paymentMethod().name())
                .param("policy", g.policySnapshot())
                .update();
    }

    @Override
    public void insertOrder(SellerOrder o) {
        jdbc.sql(
                        """
            INSERT INTO sales.seller_order
                (id, checkout_group_id, shop_id, order_number, status, item_amount, discount_amount,
                 shipping_amount, payable_amount, shop_name_snapshot, recipient_snapshot_ciphertext,
                 ship_province, ship_ward, return_policy_id, payment_deadline)
            VALUES (:id, :group, :shop, :number, :status, :items, :discount, :shipping, :payable,
                    :shopName, :recipient, :province, :ward, :policy, :deadline)
            """)
                .param("id", o.id())
                .param("group", o.checkoutGroupId())
                .param("shop", o.shopId())
                .param("number", o.orderNumber())
                .param("status", o.status().name())
                .param("items", o.itemAmount())
                .param("discount", o.discountAmount())
                .param("shipping", o.shippingAmount())
                .param("payable", o.payableAmount())
                .param("shopName", o.shopNameSnapshot())
                .param("recipient", o.recipientSnapshotCiphertext())
                .param("province", o.shipProvince())
                .param("ward", o.shipWard())
                .param("policy", o.returnPolicyId())
                .param("deadline", o.paymentDeadline(), Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    @Override
    public void insertLine(OrderLine l) {
        jdbc.sql(
                        """
            INSERT INTO sales.order_line
                (id, seller_order_id, shop_id, sku_id, product_id, product_slug, product_name_snapshot,
                 image_key, seller_sku, variant_snapshot, quantity, unit_price, gross_amount,
                 discount_amount, net_amount)
            VALUES (:id, :order, :shop, :sku, :product, :slug, :name, :image, :sellerSku,
                    CAST(:variant AS jsonb), :quantity, :unitPrice, :gross, :discount, :net)
            """)
                .param("id", l.id())
                .param("order", l.sellerOrderId())
                .param("shop", l.shopId())
                .param("sku", l.skuId())
                .param("product", l.productId())
                .param("slug", l.productSlug())
                .param("name", l.productNameSnapshot())
                .param("image", l.imageKey(), Types.VARCHAR)
                .param("sellerSku", l.sellerSku())
                .param("variant", l.variantSnapshot())
                .param("quantity", l.quantity())
                .param("unitPrice", l.unitPrice())
                .param("gross", l.grossAmount())
                .param("discount", l.discountAmount())
                .param("net", l.netAmount())
                .update();
    }

    @Override
    public void linkReservation(UUID orderLineId, UUID reservationId) {
        jdbc.sql(
                        "INSERT INTO sales.order_line_reservation (order_line_id, reservation_id)"
                                + " VALUES (:line, :reservation)")
                .param("line", orderLineId)
                .param("reservation", reservationId)
                .update();
    }

    @Override
    public void insertHistory(OrderStatusEvent e) {
        jdbc.sql(
                        """
            INSERT INTO sales.order_status_history
                (id, seller_order_id, from_status, to_status, actor_type, actor_subject, reason)
            VALUES (:id, :order, :from, :to, :actorType, :actor, :reason)
            """)
                .param("id", e.id())
                .param("order", e.sellerOrderId())
                .param("from", e.fromStatus() == null ? null : e.fromStatus().name(), Types.VARCHAR)
                .param("to", e.toStatus().name())
                .param("actorType", e.actorType())
                .param("actor", e.actorSubject(), Types.VARCHAR)
                .param("reason", e.reason(), Types.VARCHAR)
                .update();
    }

    @Override
    public long countOpenOrders(UUID buyer, String paymentMethod) {
        return jdbc.sql(
                        """
            SELECT count(*) FROM sales.seller_order o
            JOIN sales.checkout_group g ON g.id = o.checkout_group_id
            WHERE g.buyer_account_id = :buyer AND g.payment_method = :method
              AND o.status NOT IN ('DELIVERED', 'COMPLETED', 'CANCELED', 'REJECTED')
            """)
                .param("buyer", buyer)
                .param("method", paymentMethod)
                .query(Long.class)
                .single();
    }

    @Override
    public List<SellerOrder> findOrdersOfGroup(UUID groupId) {
        return jdbc.sql(
                        "SELECT "
                                + ORDER_COLUMNS
                                + " FROM sales.seller_order o WHERE o.checkout_group_id = :group"
                                + " ORDER BY o.order_number")
                .param("group", groupId)
                .query(SellerOrder.class)
                .list();
    }

    @Override
    public Optional<SellerOrder> findOrder(UUID buyer, UUID orderId) {
        return jdbc.sql(
                        "SELECT "
                                + ORDER_COLUMNS
                                + " FROM sales.seller_order o"
                                + " JOIN sales.checkout_group g ON g.id = o.checkout_group_id"
                                + " WHERE o.id = :id AND g.buyer_account_id = :buyer")
                .param("id", orderId)
                .param("buyer", buyer)
                .query(SellerOrder.class)
                .optional();
    }

    @Override
    public List<OrderLine> findLines(UUID orderId) {
        return jdbc.sql(
                        """
            SELECT id, seller_order_id, shop_id, sku_id, product_id, product_slug,
                   product_name_snapshot, image_key, seller_sku, variant_snapshot::text AS variant_snapshot,
                   quantity, unit_price, gross_amount, discount_amount, net_amount
            FROM sales.order_line WHERE seller_order_id = :order ORDER BY product_name_snapshot, id
            """)
                .param("order", orderId)
                .query(OrderLine.class)
                .list();
    }

    @Override
    public List<OrderStatusEvent> findHistory(UUID orderId) {
        return jdbc.sql(
                        """
            SELECT id, seller_order_id, from_status, to_status, actor_type, actor_subject, reason, created_at
            FROM sales.order_status_history WHERE seller_order_id = :order ORDER BY created_at, id
            """)
                .param("order", orderId)
                .query(OrderStatusEvent.class)
                .list();
    }

    @Override
    public List<OrderSummary> listSummaries(UUID buyer, int limit, int offset) {
        return jdbc.sql(
                        """
            SELECT o.id, o.order_number, o.checkout_group_id, o.shop_name_snapshot, o.status,
                   o.payable_amount,
                   (SELECT COALESCE(SUM(l.quantity), 0)::int FROM sales.order_line l
                     WHERE l.seller_order_id = o.id) AS item_count,
                   fl.product_name_snapshot AS first_product_name, fl.image_key AS first_image_key,
                   o.created_at
            FROM sales.seller_order o
            JOIN sales.checkout_group g ON g.id = o.checkout_group_id
            LEFT JOIN LATERAL (
                SELECT l.product_name_snapshot, l.image_key FROM sales.order_line l
                WHERE l.seller_order_id = o.id ORDER BY l.product_name_snapshot, l.id LIMIT 1) fl ON true
            WHERE g.buyer_account_id = :buyer
            ORDER BY o.created_at DESC, o.id
            LIMIT :limit OFFSET :offset
            """)
                .param("buyer", buyer)
                .param("limit", limit)
                .param("offset", offset)
                .query(OrderSummary.class)
                .list();
    }

    @Override
    public Optional<SellerOrder> findOrderOfShop(UUID shopId, UUID orderId) {
        return jdbc.sql(
                        "SELECT "
                                + ORDER_COLUMNS
                                + " FROM sales.seller_order o WHERE o.id = :id AND o.shop_id = :shop")
                .param("id", orderId)
                .param("shop", shopId)
                .query(SellerOrder.class)
                .optional();
    }

    @Override
    public List<SellerOrder> listForShop(UUID shopId, String status, int limit, int offset) {
        return jdbc.sql(
                        "SELECT "
                                + ORDER_COLUMNS
                                + " FROM sales.seller_order o WHERE o.shop_id = :shop"
                                + " AND (CAST(:status AS text) IS NULL OR o.status = :status)"
                                + " ORDER BY o.created_at DESC, o.id LIMIT :limit OFFSET :offset")
                .param("shop", shopId)
                .param("status", status, Types.VARCHAR)
                .param("limit", limit)
                .param("offset", offset)
                .query(SellerOrder.class)
                .list();
    }

    @Override
    public long countForShop(UUID shopId, String status) {
        return jdbc.sql(
                        "SELECT count(*) FROM sales.seller_order o WHERE o.shop_id = :shop"
                                + " AND (CAST(:status AS text) IS NULL OR o.status = :status)")
                .param("shop", shopId)
                .param("status", status, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    @Override
    public boolean transition(UUID orderId, OrderStatus from, OrderStatus to, String cancelReason) {
        return jdbc.sql(
                                """
            UPDATE sales.seller_order
            SET status = :to, version = version + 1, updated_at = now(),
                cancel_reason = COALESCE(:reason, cancel_reason),
                confirmed_at = CASE WHEN :to = 'CONFIRMED' THEN now() ELSE confirmed_at END
            WHERE id = :id AND status = :from
            """)
                        .param("id", orderId)
                        .param("from", from.name())
                        .param("to", to.name())
                        .param("reason", cancelReason, Types.VARCHAR)
                        .update()
                == 1;
    }

    @Override
    public boolean markCanceled(UUID orderId, OrderStatus from, String reason) {
        return jdbc.sql(
                                """
            UPDATE sales.seller_order
            SET status = 'CANCELED', canceled_at = now(), cancel_reason = :reason,
                version = version + 1, updated_at = now()
            WHERE id = :id AND status = :from
            """)
                        .param("id", orderId)
                        .param("from", from.name())
                        .param("reason", reason, Types.VARCHAR)
                        .update()
                == 1;
    }

    @Override
    public boolean markShipped(UUID orderId, String trackingCode) {
        return jdbc.sql(
                                """
            UPDATE sales.seller_order
            SET status = 'IN_FULFILLMENT', manual_tracking_code = :code, shipped_at = now(),
                version = version + 1, updated_at = now()
            WHERE id = :id AND status = 'READY_TO_SHIP'
            """)
                        .param("id", orderId)
                        .param("code", trackingCode)
                        .update()
                == 1;
    }

    @Override
    public boolean markDelivered(UUID orderId, int returnWindowDays) {
        return jdbc.sql(
                                """
            UPDATE sales.seller_order
            SET status = 'DELIVERED', delivered_at = now(),
                return_eligible_until = now() + make_interval(days => :days),
                version = version + 1, updated_at = now()
            WHERE id = :id AND status = 'IN_FULFILLMENT'
            """)
                        .param("id", orderId)
                        .param("days", returnWindowDays)
                        .update()
                == 1;
    }

    @Override
    public List<vn.vshop.modules.order.model.OverdueOrders.Overdue> findOverdue(
            java.time.OffsetDateTime cutoff, int limit) {
        return jdbc.sql(
                        """
            SELECT o.id, o.order_number, o.shop_id, o.shop_name_snapshot AS shop_name,
                   o.payable_amount, o.created_at,
                   (EXTRACT(EPOCH FROM (CAST(:cutoff AS timestamptz) - o.created_at)) / 60)::bigint
                       AS minutes_overdue
            FROM sales.seller_order o
            WHERE o.status = 'AWAITING_SELLER' AND o.created_at <= :cutoff
            ORDER BY o.created_at, o.id LIMIT :limit
            """)
                .param("cutoff", cutoff)
                .param("limit", limit)
                .query(vn.vshop.modules.order.model.OverdueOrders.Overdue.class)
                .list();
    }

    @Override
    public SellerOrder findById(UUID orderId) {
        return jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM sales.seller_order o WHERE o.id = :id")
                .param("id", orderId)
                .query(SellerOrder.class)
                .single();
    }

    @Override
    public String paymentMethodOfGroup(UUID groupId) {
        return jdbc.sql("SELECT payment_method FROM sales.checkout_group WHERE id = :id")
                .param("id", groupId)
                .query(String.class)
                .single();
    }

    @Override
    public boolean reopenForShipping(UUID orderId) {
        return jdbc.sql(
                                """
            UPDATE sales.seller_order
            SET status = 'READY_TO_SHIP', manual_tracking_code = NULL, shipped_at = NULL,
                version = version + 1, updated_at = now()
            WHERE id = :id AND status = 'IN_FULFILLMENT'
            """)
                        .param("id", orderId)
                        .update()
                == 1;
    }

    @Override
    public List<UUID> findReservationIds(UUID orderId) {
        return jdbc.sql(
                        """
            SELECT r.reservation_id FROM sales.order_line_reservation r
            JOIN sales.order_line l ON l.id = r.order_line_id
            WHERE l.seller_order_id = :order ORDER BY r.reservation_id
            """)
                .param("order", orderId)
                .query(UUID.class)
                .list();
    }

    @Override
    public long countOrders(UUID buyer) {
        return jdbc.sql(
                        """
            SELECT count(*) FROM sales.seller_order o
            JOIN sales.checkout_group g ON g.id = o.checkout_group_id
            WHERE g.buyer_account_id = :buyer
            """)
                .param("buyer", buyer)
                .query(Long.class)
                .single();
    }
}
