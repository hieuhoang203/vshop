package vn.vshop.modules.order.repository.jdbc;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.order.entity.PickupAddress;
import vn.vshop.modules.order.entity.Shipment;
import vn.vshop.modules.order.repository.ShipmentRepository;

@Repository
public class JdbcShipmentRepository implements ShipmentRepository {
    private static final String COLUMNS =
            "SELECT id, seller_order_id, shop_id, direction, carrier_code, partner_order_id, tracking_number,"
                    + " status, carrier_status_id, cod_amount, weight_grams, declared_value,"
                    + " carrier_fee, insurance_fee, failure_message, created_at, updated_at"
                    + " FROM fulfillment.shipment";

    private final JdbcClient jdbc;

    public JdbcShipmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PickupAddress> findPickup(UUID shopId) {
        return jdbc.sql(
                        "SELECT shop_id, contact_name, phone_ciphertext, street_ciphertext, ward,"
                                + " district, province, updated_at FROM fulfillment.pickup_address"
                                + " WHERE shop_id = :s")
                .param("s", shopId)
                .query(PickupAddress.class)
                .optional();
    }

    @Override
    public void savePickup(PickupAddress a) {
        jdbc.sql(
                        "INSERT INTO fulfillment.pickup_address (shop_id, contact_name,"
                                + " phone_ciphertext, street_ciphertext, ward, district, province)"
                                + " VALUES (:shop, :name, :phone, :street, :ward, :district, :province)"
                                + " ON CONFLICT (shop_id) DO UPDATE SET contact_name = :name,"
                                + " phone_ciphertext = :phone, street_ciphertext = :street,"
                                + " ward = :ward, district = :district, province = :province,"
                                + " updated_at = now()")
                .param("shop", a.shopId())
                .param("name", a.contactName())
                .param("phone", a.phoneCiphertext())
                .param("street", a.streetCiphertext())
                .param("ward", a.ward())
                .param("district", a.district())
                .param("province", a.province())
                .update();
    }

    @Override
    public Optional<Shipment> findLiveByOrder(UUID orderId) {
        return jdbc.sql(
                        COLUMNS
                                + " WHERE seller_order_id = :o AND direction = 'FORWARD'"
                                + " AND status NOT IN ('FAILED', 'CANCELED')"
                                + " ORDER BY created_at DESC LIMIT 1")
                .param("o", orderId)
                .query(Shipment.class)
                .optional();
    }

    @Override
    public Optional<Shipment> findFailedByOrder(UUID orderId) {
        return jdbc.sql(
                        COLUMNS
                                + " WHERE seller_order_id = :o AND direction = 'FORWARD'"
                                + " AND status = 'FAILED' ORDER BY created_at DESC LIMIT 1")
                .param("o", orderId)
                .query(Shipment.class)
                .optional();
    }

    @Override
    public Optional<Shipment> findByTracking(String carrier, String trackingNumber) {
        return jdbc.sql(COLUMNS + " WHERE carrier_code = :c AND tracking_number = :t")
                .param("c", carrier)
                .param("t", trackingNumber)
                .query(Shipment.class)
                .optional();
    }

    @Override
    public Optional<Shipment> findByPartnerOrder(String carrier, String partnerOrderId) {
        return jdbc.sql(COLUMNS + " WHERE carrier_code = :c AND partner_order_id = :p")
                .param("c", carrier)
                .param("p", partnerOrderId)
                .query(Shipment.class)
                .optional();
    }

    @Override
    public long countByOrder(UUID orderId) {
        return jdbc.sql("SELECT count(*) FROM fulfillment.shipment WHERE seller_order_id = :o")
                .param("o", orderId)
                .query(Long.class)
                .single();
    }

    @Override
    public void insertCreating(
            UUID id,
            UUID orderId,
            UUID shopId,
            String carrier,
            String partnerOrderId,
            long codAmount,
            int weightGrams,
            long declaredValue) {
        jdbc.sql(
                        "INSERT INTO fulfillment.shipment (id, seller_order_id, shop_id, carrier_code,"
                                + " partner_order_id, status, cod_amount, weight_grams, declared_value)"
                                + " VALUES (:id, :order, :shop, :carrier, :partner, 'CREATING', :cod,"
                                + " :weight, :value)")
                .param("id", id)
                .param("order", orderId)
                .param("shop", shopId)
                .param("carrier", carrier)
                .param("partner", partnerOrderId)
                .param("cod", codAmount)
                .param("weight", weightGrams)
                .param("value", declaredValue)
                .update();
    }

    @Override
    public Optional<Shipment> findLiveReturnByOrder(UUID orderId) {
        return jdbc.sql(
                        COLUMNS
                                + " WHERE seller_order_id = :o AND direction = 'RETURN'"
                                + " AND status NOT IN ('FAILED', 'CANCELED')"
                                + " ORDER BY created_at DESC LIMIT 1")
                .param("o", orderId)
                .query(Shipment.class)
                .optional();
    }

    @Override
    public Optional<Shipment> findFailedReturnByOrder(UUID orderId) {
        return jdbc.sql(
                        COLUMNS
                                + " WHERE seller_order_id = :o AND direction = 'RETURN'"
                                + " AND status = 'FAILED' ORDER BY created_at DESC LIMIT 1")
                .param("o", orderId)
                .query(Shipment.class)
                .optional();
    }

    @Override
    public void insertCreatingReturn(
            UUID id,
            UUID orderId,
            UUID shopId,
            String carrier,
            String partnerOrderId,
            int weightGrams,
            long declaredValue) {
        jdbc.sql(
                        "INSERT INTO fulfillment.shipment (id, seller_order_id, shop_id, direction,"
                                + " carrier_code, partner_order_id, status, cod_amount, weight_grams,"
                                + " declared_value) VALUES (:id, :order, :shop, 'RETURN', :carrier,"
                                + " :partner, 'CREATING', 0, :weight, :value)")
                .param("id", id)
                .param("order", orderId)
                .param("shop", shopId)
                .param("carrier", carrier)
                .param("partner", partnerOrderId)
                .param("weight", weightGrams)
                .param("value", declaredValue)
                .update();
    }

    @Override
    public void reopen(UUID id, long codAmount, int weightGrams, long declaredValue) {
        jdbc.sql(
                        "UPDATE fulfillment.shipment SET status = 'CREATING', failure_message = NULL,"
                                + " cod_amount = :cod, weight_grams = :weight,"
                                + " declared_value = :value, updated_at = now() WHERE id = :id")
                .param("id", id)
                .param("cod", codAmount)
                .param("weight", weightGrams)
                .param("value", declaredValue)
                .update();
    }

    @Override
    public void markCreated(UUID id, String trackingNumber, Long fee, Long insuranceFee) {
        jdbc.sql(
                        "UPDATE fulfillment.shipment SET status = 'CREATED', tracking_number = :t,"
                                + " carrier_fee = :fee, insurance_fee = :ins, failure_message = NULL,"
                                + " updated_at = now() WHERE id = :id")
                .param("t", trackingNumber)
                .param("fee", fee, Types.BIGINT)
                .param("ins", insuranceFee, Types.BIGINT)
                .param("id", id)
                .update();
    }

    @Override
    public void markFailed(UUID id, String message) {
        jdbc.sql(
                        "UPDATE fulfillment.shipment SET status = 'FAILED', failure_message = :m,"
                                + " updated_at = now() WHERE id = :id")
                .param(
                        "m",
                        message == null
                                ? null
                                : message.substring(0, Math.min(500, message.length())),
                        Types.VARCHAR)
                .param("id", id)
                .update();
    }

    @Override
    public void advance(UUID id, String status, int carrierStatusId) {
        jdbc.sql(
                        "UPDATE fulfillment.shipment SET status = :s, carrier_status_id = :c,"
                                + " updated_at = now() WHERE id = :id"
                                + " AND status NOT IN ('DELIVERED', 'RETURNED', 'CANCELED', 'FAILED')")
                .param("s", status)
                .param("c", carrierStatusId)
                .param("id", id)
                .update();
    }

    @Override
    public void cancel(UUID id) {
        jdbc.sql(
                        "UPDATE fulfillment.shipment SET status = 'CANCELED', updated_at = now()"
                                + " WHERE id = :id AND status NOT IN ('DELIVERED', 'RETURNED')")
                .param("id", id)
                .update();
    }

    @Override
    public boolean insertEvent(
            UUID shipmentId,
            String eventId,
            String rawStatus,
            String normalized,
            String reasonCode,
            String reason,
            OffsetDateTime occurredAt) {
        return jdbc.sql(
                                "INSERT INTO fulfillment.shipment_event (id, shipment_id,"
                                        + " provider_event_id, raw_status, normalized_status,"
                                        + " reason_code, reason, occurred_at)"
                                        + " VALUES (:id, :shipment, :event, :raw, :norm, :code, :reason, :at)"
                                        + " ON CONFLICT (shipment_id, provider_event_id) DO NOTHING")
                        .param("id", UUID.randomUUID())
                        .param("shipment", shipmentId)
                        .param("event", eventId)
                        .param("raw", rawStatus)
                        .param("norm", normalized)
                        .param("code", reasonCode, Types.VARCHAR)
                        .param("reason", reason, Types.VARCHAR)
                        .param("at", occurredAt)
                        .update()
                == 1;
    }
}
