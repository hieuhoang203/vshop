package vn.vshop.modules.order.repository.jdbc;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.order.entity.ReturnCase;
import vn.vshop.modules.order.entity.ReturnCaseEvent;
import vn.vshop.modules.order.repository.ReturnCaseRepository;

@Repository
public class JdbcReturnCaseRepository implements ReturnCaseRepository {
    private static final String SELECT =
            """
            SELECT c.id, c.seller_order_id, o.order_number, c.shop_id, c.buyer_account_id, c.status,
                   c.reason_code, c.preferred_resolution, c.resolution, c.description,
                   c.requested_amount, c.offered_amount, c.approved_amount, c.policy_id,
                   c.seller_deadline_at, c.seller_note, c.admin_note, c.return_tracking_code,
                   c.version, c.created_at, c.updated_at
            FROM aftersales.return_case c JOIN sales.seller_order o ON o.id = c.seller_order_id
            """;

    private final JdbcClient jdbc;

    public JdbcReturnCaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(
            UUID id,
            UUID orderId,
            UUID shopId,
            UUID buyer,
            String reason,
            String preferred,
            String description,
            long requestedAmount,
            UUID policyId,
            OffsetDateTime sellerDeadline) {
        jdbc.sql(
                        "INSERT INTO aftersales.return_case (id, seller_order_id, shop_id,"
                                + " buyer_account_id, status, reason_code, preferred_resolution,"
                                + " description, requested_amount, policy_id, seller_deadline_at)"
                                + " VALUES (:id, :order, :shop, :buyer, 'REQUESTED', :reason, :preferred,"
                                + " :description, :amount, :policy, :deadline)")
                .param("id", id)
                .param("order", orderId)
                .param("shop", shopId)
                .param("buyer", buyer)
                .param("reason", reason)
                .param("preferred", preferred)
                .param("description", description)
                .param("amount", requestedAmount)
                .param("policy", policyId)
                .param("deadline", sellerDeadline)
                .update();
    }

    @Override
    public void insertItem(UUID caseId, UUID orderLineId, int quantity) {
        jdbc.sql(
                        "INSERT INTO aftersales.return_case_item (return_case_id, order_line_id,"
                                + " quantity) VALUES (:c, :l, :q)")
                .param("c", caseId)
                .param("l", orderLineId)
                .param("q", quantity)
                .update();
    }

    @Override
    public List<vn.vshop.modules.order.entity.ReturnCaseItem> items(UUID caseId) {
        return jdbc.sql(
                        "SELECT i.order_line_id, l.product_name_snapshot AS product_name,"
                                + " i.quantity, (l.net_amount * i.quantity / l.quantity) AS amount"
                                + " FROM aftersales.return_case_item i"
                                + " JOIN sales.order_line l ON l.id = i.order_line_id"
                                + " WHERE i.return_case_id = :c ORDER BY l.id")
                .param("c", caseId)
                .query(vn.vshop.modules.order.entity.ReturnCaseItem.class)
                .list();
    }

    @Override
    public boolean isPartial(UUID caseId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM aftersales.return_case_item WHERE return_case_id = :c)"
                                + " AND EXISTS (SELECT 1 FROM aftersales.return_case rc"
                                + " JOIN sales.order_line l ON l.seller_order_id = rc.seller_order_id"
                                + " WHERE rc.id = :c AND COALESCE((SELECT i.quantity"
                                + " FROM aftersales.return_case_item i WHERE i.return_case_id = rc.id"
                                + " AND i.order_line_id = l.id), 0) < l.quantity)")
                .param("c", caseId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<ReturnCase> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id")
                .param("id", id)
                .query(ReturnCase.class)
                .optional();
    }

    @Override
    public Optional<ReturnCase> findLiveByOrder(UUID orderId) {
        return jdbc.sql(
                        SELECT
                                + " WHERE c.seller_order_id = :o AND c.status <> 'CANCELED'"
                                + " ORDER BY c.created_at DESC LIMIT 1")
                .param("o", orderId)
                .query(ReturnCase.class)
                .optional();
    }

    @Override
    public List<ReturnCase> listForShop(UUID shopId, String status, int limit, int offset) {
        return jdbc.sql(
                        SELECT
                                + " WHERE c.shop_id = :shop"
                                + " AND (CAST(:status AS text) IS NULL OR c.status = :status)"
                                + " ORDER BY c.created_at DESC, c.id LIMIT :limit OFFSET :offset")
                .param("shop", shopId)
                .param("status", status, Types.VARCHAR)
                .param("limit", limit)
                .param("offset", offset)
                .query(ReturnCase.class)
                .list();
    }

    @Override
    public long countForShop(UUID shopId, String status) {
        return jdbc.sql(
                        "SELECT count(*) FROM aftersales.return_case c WHERE c.shop_id = :shop"
                                + " AND (CAST(:status AS text) IS NULL OR c.status = :status)")
                .param("shop", shopId)
                .param("status", status, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    @Override
    public List<ReturnCase> listForAdmin(OffsetDateTime now, int limit) {
        return jdbc.sql(
                        SELECT
                                + " WHERE c.status = 'REVIEWING'"
                                + " OR (c.status = 'REQUESTED' AND c.seller_deadline_at < :now)"
                                + " ORDER BY c.created_at, c.id LIMIT :limit")
                .param("now", now)
                .param("limit", limit)
                .query(ReturnCase.class)
                .list();
    }

    @Override
    public boolean move(UUID id, Collection<String> from, String to, Change c) {
        return jdbc.sql(
                                """
            UPDATE aftersales.return_case SET status = :to, version = version + 1, updated_at = now(),
                offered_amount = COALESCE(:offered, offered_amount),
                approved_amount = COALESCE(:approved, approved_amount),
                resolution = COALESCE(:resolution, resolution),
                seller_note = COALESCE(:sellerNote, seller_note),
                admin_note = COALESCE(:adminNote, admin_note),
                return_tracking_code = COALESCE(:tracking, return_tracking_code)
            WHERE id = :id AND status IN (:from)
            """)
                        .param("to", to)
                        .param("offered", c.offeredAmount(), Types.BIGINT)
                        .param("approved", c.approvedAmount(), Types.BIGINT)
                        .param("resolution", c.resolution(), Types.VARCHAR)
                        .param("sellerNote", c.sellerNote(), Types.VARCHAR)
                        .param("adminNote", c.adminNote(), Types.VARCHAR)
                        .param("tracking", c.trackingCode(), Types.VARCHAR)
                        .param("id", id)
                        .param("from", from)
                        .update()
                == 1;
    }

    @Override
    public void insertEvent(
            UUID caseId, String from, String to, String actorType, String actor, String note) {
        jdbc.sql(
                        "INSERT INTO aftersales.return_case_event (id, return_case_id, from_status,"
                                + " to_status, actor_type, actor_subject, note)"
                                + " VALUES (:id, :case, :from, :to, :type, :actor, :note)")
                .param("id", UUID.randomUUID())
                .param("case", caseId)
                .param("from", from, Types.VARCHAR)
                .param("to", to)
                .param("type", actorType)
                .param("actor", actor, Types.VARCHAR)
                .param("note", note, Types.VARCHAR)
                .update();
    }

    @Override
    public List<ReturnCaseEvent> events(UUID caseId) {
        return jdbc.sql(
                        "SELECT id, return_case_id, from_status, to_status, actor_type, actor_subject,"
                                + " note, created_at FROM aftersales.return_case_event"
                                + " WHERE return_case_id = :c ORDER BY created_at, id")
                .param("c", caseId)
                .query(ReturnCaseEvent.class)
                .list();
    }
}
