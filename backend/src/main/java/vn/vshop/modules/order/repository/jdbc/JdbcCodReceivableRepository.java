package vn.vshop.modules.order.repository.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.order.entity.CodReceivable;
import vn.vshop.modules.order.repository.CodReceivableRepository;

@Repository
public class JdbcCodReceivableRepository implements CodReceivableRepository {
    private static final String SELECT =
            """
            SELECT c.id, c.seller_order_id, o.order_number, c.shop_id, c.amount,
                   COALESCE((SELECT SUM(r.amount) FROM payment.refund r
                             WHERE r.seller_order_id = c.seller_order_id
                               AND r.source = 'COD' AND r.status <> 'FAILED'), 0) AS refunded_amount,
                   c.collected_by, c.status, c.settled_reference, c.settled_at, c.created_at
            FROM payment.cod_receivable c JOIN sales.seller_order o ON o.id = c.seller_order_id
            """;

    private final JdbcClient jdbc;

    public JdbcCodReceivableRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertIfAbsent(
            UUID orderId, UUID shopId, long amount, String collectedBy, String status) {
        jdbc.sql(
                        "INSERT INTO payment.cod_receivable (id, seller_order_id, shop_id, amount,"
                                + " collected_by, status, settled_reference, settled_at)"
                                + " VALUES (:id, :o, :shop, :amount, :by, :status,"
                                + " CASE WHEN :status = 'SETTLED' THEN 'SHOP-COLLECTED' END,"
                                + " CASE WHEN :status = 'SETTLED' THEN now() END)"
                                + " ON CONFLICT (seller_order_id) DO NOTHING")
                .param("id", UUID.randomUUID())
                .param("o", orderId)
                .param("shop", shopId)
                .param("amount", amount)
                .param("by", collectedBy)
                .param("status", status)
                .update();
    }

    @Override
    public List<CodReceivable> list(String status, int limit) {
        String where = status == null ? "" : " WHERE c.status = :status";
        var query = jdbc.sql(SELECT + where + " ORDER BY c.created_at DESC LIMIT :n");
        if (status != null) {
            query = query.param("status", status);
        }
        return query.param("n", limit).query(CodReceivable.class).list();
    }

    @Override
    public Optional<CodReceivable> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id")
                .param("id", id)
                .query(CodReceivable.class)
                .optional();
    }

    @Override
    public boolean settle(UUID id, String reference, String subject) {
        return jdbc.sql(
                                "UPDATE payment.cod_receivable SET status = 'SETTLED',"
                                        + " settled_reference = :ref, settled_by_subject = :subject,"
                                        + " settled_at = now() WHERE id = :id AND status = 'WITH_CARRIER'")
                        .param("ref", reference)
                        .param("subject", subject)
                        .param("id", id)
                        .update()
                == 1;
    }
}
