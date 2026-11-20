package vn.vshop.modules.order.repository.jdbc;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.order.entity.Refund;
import vn.vshop.modules.order.repository.RefundRepository;

@Repository
public class JdbcRefundRepository implements RefundRepository {
    private static final String SELECT =
            """
            SELECT r.id, r.charge_id, r.checkout_group_id, r.seller_order_id, o.order_number,
                   r.reason, r.status, r.amount, r.bank_name, r.account_number_ciphertext,
                   r.account_name_ciphertext, r.payout_reference, r.note, r.paid_at, r.created_at
            FROM payment.refund r JOIN sales.seller_order o ON o.id = r.seller_order_id
            """;

    private final JdbcClient jdbc;

    public JdbcRefundRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Refundable> lockRefundable(UUID orderId) {
        return jdbc.sql(
                        """
            SELECT al.id AS allocation_id, al.charge_id, al.checkout_group_id,
                   al.amount - COALESCE((
                       SELECT SUM(ra.amount) FROM payment.refund_allocation ra
                       JOIN payment.refund r ON r.id = ra.refund_id
                       WHERE ra.allocation_id = al.id AND r.status <> 'FAILED'), 0) AS amount
            FROM payment.allocation al
            WHERE al.seller_order_id = :order
            ORDER BY al.id
            FOR UPDATE OF al
            """)
                .param("order", orderId)
                .query(Refundable.class)
                .list();
    }

    @Override
    public boolean insertRefund(
            UUID id,
            UUID chargeId,
            UUID groupId,
            UUID orderId,
            String reason,
            String idempotencyKey,
            long amount) {
        return jdbc.sql(
                                "INSERT INTO payment.refund (id, charge_id, checkout_group_id,"
                                        + " seller_order_id, reason, idempotency_key, amount, status)"
                                        + " VALUES (:id, :charge, :group, :order, :reason, :key, :amount,"
                                        + " 'AWAITING_ACCOUNT')"
                                        + " ON CONFLICT (idempotency_key) DO NOTHING")
                        .param("id", id)
                        .param("charge", chargeId)
                        .param("group", groupId)
                        .param("order", orderId)
                        .param("reason", reason)
                        .param("key", idempotencyKey)
                        .param("amount", amount)
                        .update()
                == 1;
    }

    @Override
    public void insertAllocation(UUID refundId, UUID chargeId, UUID allocationId, long amount) {
        jdbc.sql(
                        "INSERT INTO payment.refund_allocation (refund_id, charge_id, allocation_id,"
                                + " amount) VALUES (:refund, :charge, :allocation, :amount)")
                .param("refund", refundId)
                .param("charge", chargeId)
                .param("allocation", allocationId)
                .param("amount", amount)
                .update();
    }

    @Override
    public List<Refund> findByOrder(UUID orderId) {
        return jdbc.sql(SELECT + " WHERE r.seller_order_id = :o ORDER BY r.created_at, r.id")
                .param("o", orderId)
                .query(Refund.class)
                .list();
    }

    @Override
    public Optional<Refund> find(UUID refundId) {
        return jdbc.sql(SELECT + " WHERE r.id = :id")
                .param("id", refundId)
                .query(Refund.class)
                .optional();
    }

    @Override
    public List<Refund> list(String status, int limit) {
        return jdbc.sql(
                        SELECT
                                + " WHERE (CAST(:status AS text) IS NULL OR r.status = :status)"
                                + " ORDER BY r.created_at, r.id LIMIT :limit")
                .param("status", status, Types.VARCHAR)
                .param("limit", limit)
                .query(Refund.class)
                .list();
    }

    @Override
    public boolean setAccount(
            UUID id, String bankName, String numberCiphertext, String nameCiphertext) {
        return jdbc.sql(
                                "UPDATE payment.refund SET status = 'APPROVED', bank_name = :bank,"
                                        + " account_number_ciphertext = :number,"
                                        + " account_name_ciphertext = :name, note = NULL, updated_at = now()"
                                        + " WHERE id = :id AND status IN ('AWAITING_ACCOUNT', 'APPROVED')")
                        .param("bank", bankName)
                        .param("number", numberCiphertext)
                        .param("name", nameCiphertext)
                        .param("id", id)
                        .update()
                == 1;
    }

    @Override
    public boolean markPaid(UUID id, String payoutReference, String note, String subject) {
        return jdbc.sql(
                                "UPDATE payment.refund SET status = 'SUCCEEDED', payout_reference = :ref,"
                                        + " note = :note, paid_by_subject = :subject, paid_at = now(),"
                                        + " updated_at = now() WHERE id = :id AND status = 'APPROVED'")
                        .param("ref", payoutReference)
                        .param("note", note, Types.VARCHAR)
                        .param("subject", subject)
                        .param("id", id)
                        .update()
                == 1;
    }

    @Override
    public boolean badAccount(UUID id, String note) {
        return jdbc.sql(
                                "UPDATE payment.refund SET status = 'AWAITING_ACCOUNT', bank_name = NULL,"
                                        + " account_number_ciphertext = NULL, account_name_ciphertext = NULL,"
                                        + " note = :note, updated_at = now()"
                                        + " WHERE id = :id AND status = 'APPROVED'")
                        .param("note", note)
                        .param("id", id)
                        .update()
                == 1;
    }
}
