package vn.vshop.modules.order.repository.jdbc;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.order.entity.PaymentAttempt;
import vn.vshop.modules.order.entity.PaymentIntent;
import vn.vshop.modules.order.repository.PaymentRepository;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {
    private static final String INTENT =
            "SELECT id, checkout_group_id, merchant_reference, requested_amount, status,"
                    + " payment_deadline, created_at FROM payment.intent";
    private static final String ATTEMPT =
            "SELECT id, intent_id, checkout_group_id, provider, merchant_attempt_reference,"
                    + " provider_reference, status, client_ip, checkout_url, created_at, expires_at"
                    + " FROM payment.attempt";

    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertIntent(
            UUID id, UUID groupId, String reference, long amount, OffsetDateTime deadline) {
        jdbc.sql(
                        "INSERT INTO payment.intent (id, checkout_group_id, merchant_reference,"
                                + " requested_amount, status, payment_deadline)"
                                + " VALUES (:id, :group, :ref, :amount, 'CREATED', :deadline)")
                .param("id", id)
                .param("group", groupId)
                .param("ref", reference)
                .param("amount", amount)
                .param("deadline", deadline)
                .update();
    }

    @Override
    public void insertAttempt(
            UUID id,
            UUID intentId,
            UUID groupId,
            String provider,
            String reference,
            String clientIp,
            OffsetDateTime expiresAt) {
        jdbc.sql(
                        "INSERT INTO payment.attempt (id, intent_id, checkout_group_id, provider,"
                                + " merchant_attempt_reference, status, client_ip, expires_at, created_at)"
                                + " VALUES (:id, :intent, :group, :provider, :ref, 'CREATED', :ip, :expires,"
                                + " clock_timestamp())")
                .param("id", id)
                .param("intent", intentId)
                .param("group", groupId)
                .param("provider", provider)
                .param("ref", reference)
                .param("ip", clientIp)
                .param("expires", expiresAt)
                .update();
    }

    @Override
    public Optional<PaymentIntent> findIntentByGroup(UUID groupId) {
        return jdbc.sql(INTENT + " WHERE checkout_group_id = :g")
                .param("g", groupId)
                .query(PaymentIntent.class)
                .optional();
    }

    @Override
    public long nextOrderCode() {
        return jdbc.sql("SELECT nextval('payment.order_code_seq')").query(Long.class).single();
    }

    @Override
    public boolean setCheckout(UUID attemptId, String url, String providerReference) {
        return jdbc.sql(
                                "UPDATE payment.attempt SET checkout_url = :url,"
                                        + " provider_reference = COALESCE(:p, provider_reference)"
                                        + " WHERE id = :id AND checkout_url IS NULL")
                        .param("url", url)
                        .param("p", providerReference, Types.VARCHAR)
                        .param("id", attemptId)
                        .update()
                == 1;
    }

    @Override
    public Optional<PaymentIntent> findIntentForUpdate(UUID intentId) {
        return jdbc.sql(INTENT + " WHERE id = :id FOR UPDATE")
                .param("id", intentId)
                .query(PaymentIntent.class)
                .optional();
    }

    @Override
    public Optional<PaymentAttempt> findLatestAttempt(UUID intentId) {
        return jdbc.sql(ATTEMPT + " WHERE intent_id = :i ORDER BY created_at DESC LIMIT 1")
                .param("i", intentId)
                .query(PaymentAttempt.class)
                .optional();
    }

    @Override
    public Optional<PaymentAttempt> findAttemptByReferenceForUpdate(String reference) {
        return jdbc.sql(ATTEMPT + " WHERE merchant_attempt_reference = :r FOR UPDATE")
                .param("r", reference)
                .query(PaymentAttempt.class)
                .optional();
    }

    @Override
    public void setIntentStatus(UUID intentId, String status) {
        jdbc.sql("UPDATE payment.intent SET status = :s, updated_at = now() WHERE id = :id")
                .param("s", status)
                .param("id", intentId)
                .update();
    }

    @Override
    public void setAttemptStatus(UUID attemptId, String status, String providerReference) {
        jdbc.sql(
                        "UPDATE payment.attempt SET status = :s,"
                                + " provider_reference = COALESCE(:p, provider_reference) WHERE id = :id")
                .param("s", status)
                .param("p", providerReference, Types.VARCHAR)
                .param("id", attemptId)
                .update();
    }

    @Override
    public boolean insertCharge(
            UUID id,
            UUID attemptId,
            UUID groupId,
            String provider,
            String providerReference,
            long amount) {
        return jdbc.sql(
                                "INSERT INTO payment.charge (id, attempt_id, checkout_group_id, provider,"
                                        + " provider_charge_reference, amount, captured_at)"
                                        + " VALUES (:id, :attempt, :group, :provider, :ref, :amount, now())"
                                        + " ON CONFLICT (provider, provider_charge_reference) DO NOTHING")
                        .param("id", id)
                        .param("attempt", attemptId)
                        .param("group", groupId)
                        .param("provider", provider)
                        .param("ref", providerReference)
                        .param("amount", amount)
                        .update()
                == 1;
    }

    @Override
    public void insertAllocation(UUID id, UUID chargeId, UUID orderId, UUID groupId, long amount) {
        jdbc.sql(
                        "INSERT INTO payment.allocation (id, charge_id, seller_order_id,"
                                + " checkout_group_id, amount)"
                                + " VALUES (:id, :charge, :order, :group, :amount)")
                .param("id", id)
                .param("charge", chargeId)
                .param("order", orderId)
                .param("group", groupId)
                .param("amount", amount)
                .update();
    }

    @Override
    public boolean insertEvent(String provider, String eventKey, String reference, String outcome) {
        return jdbc.sql(
                                "INSERT INTO payment.provider_event (id, provider, event_key,"
                                        + " merchant_reference, outcome)"
                                        + " VALUES (:id, :provider, :key, :ref, :outcome)"
                                        + " ON CONFLICT (provider, event_key) DO NOTHING")
                        .param("id", UUID.randomUUID())
                        .param("provider", provider)
                        .param("key", eventKey)
                        .param("ref", reference, Types.VARCHAR)
                        .param("outcome", outcome)
                        .update()
                == 1;
    }

    @Override
    public void insertReview(UUID chargeId, UUID groupId, String reason) {
        jdbc.sql(
                        "INSERT INTO payment.review_case (id, charge_id, checkout_group_id, reason)"
                                + " VALUES (:id, :charge, :group, :reason)"
                                + " ON CONFLICT (charge_id, reason) DO NOTHING")
                .param("id", UUID.randomUUID())
                .param("charge", chargeId)
                .param("group", groupId)
                .param("reason", reason)
                .update();
    }

    @Override
    public List<PaymentIntent> lockOverdueIntents(OffsetDateTime now, int limit) {
        return jdbc.sql(
                        INTENT
                                + " WHERE status IN ('CREATED', 'PENDING') AND payment_deadline < :now"
                                + " ORDER BY payment_deadline LIMIT :limit FOR UPDATE SKIP LOCKED")
                .param("now", now)
                .param("limit", limit)
                .query(PaymentIntent.class)
                .list();
    }
}
