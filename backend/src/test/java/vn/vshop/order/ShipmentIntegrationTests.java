package vn.vshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.modules.order.gateway.GhtkCarrier;
import vn.vshop.modules.order.gateway.ShippingCarrier;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.service.PolicyService;

/** Shipping through GHTK with the carrier calls stubbed: what we send and how we react. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.shipping.ghtk.token=test-token",
            "vshop.shipping.ghtk.partner-code=TESTPARTNER",
            "vshop.shipping.ghtk.webhook-token=test-hook-secret"
        })
@AutoConfigureMockMvc
@Transactional
class ShipmentIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SELLER = "ship-seller";
    private static final String BASE = "/api/v1/seller/shops/";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PolicyService policies;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoSpyBean GhtkCarrier carrier;

    private UUID shop;
    private UUID sku;

    @BeforeEach
    void seed() {
        UUID category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Ship Test',:slug)")
                .param("id", category)
                .param("slug", "ship-" + category)
                .update();
        shop = UUID.randomUUID();
        jdbc.sql(
                        "WITH a AS (INSERT INTO identity.account(external_subject) VALUES (:o) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,a.id,'Ship Shop','Shipping test shop description','APPROVED' FROM a")
                .param("o", SELLER)
                .param("id", shop)
                .update();
        sku = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Ship product','Shipping test product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "ship-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,100000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "SHIP-" + sku)
                .update();
        UUID warehouse = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:shop,'W1','Kho')")
                .param("id", warehouse)
                .param("shop", shop)
                .update();
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand) VALUES (:sku,:wh,:shop,10)")
                .param("sku", sku)
                .param("wh", warehouse)
                .param("shop", shop)
                .update();
        for (var entry :
                Map.of(
                                PolicyType.SHIPPING_FLAT, Map.of("amount", 25_000L),
                                PolicyType.RETURN, Map.of("windowDays", 7L),
                                PolicyType.COD_LIMITS,
                                        Map.of("maxOrderAmount", 5_000_000L, "maxOpenOrders", 5L),
                                PolicyType.PAYMENT_WINDOW,
                                        Map.of(
                                                "paymentMinutes",
                                                15L,
                                                "reservationBufferMinutes",
                                                5L))
                        .entrySet()) {
            policies.approve(
                    "ship-admin",
                    policies.createDraft("ship-admin", entry.getKey(), entry.getValue()).id());
        }
    }

    @Test
    void aShipmentNeedsTheCarrierAndAPickupAddressAndIsRefusedBeforeReady() throws Exception {
        String order = readyOrder();
        // Not configured: shops stay on manual shipping.
        doReturn(false).when(carrier).configured();
        createShipment(order, 700)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARRIER_NOT_CONFIGURED"));
        doReturn(true).when(carrier).configured();
        createShipment(order, 700)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PICKUP_ADDRESS_REQUIRED"));
        // Invalid weights are refused at the edge.
        savePickup();
        createShipment(order, 10).andExpect(status().isBadRequest());
        // Only READY_TO_SHIP orders can be handed over.
        String early = placedOrder();
        createShipment(early, 700)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        verify(carrier, never()).createShipment(any());
    }

    @Test
    void theParcelIsCreatedOnceWithTheRightDataAndDeliveryComesFromTheCarrier() throws Exception {
        String order = readyOrder();
        savePickup();
        mvc.perform(get(BASE + shop + "/pickup-address").with(seller()))
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.district").value("Quan 1"));
        assertThat(
                        jdbc.sql("SELECT phone_ciphertext FROM fulfillment.pickup_address")
                                .query(String.class)
                                .single())
                .startsWith("v1.")
                .doesNotContain("0901234567");

        doReturn(created("S900.A1")).when(carrier).createShipment(any());
        createShipment(order, 700)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_FULFILLMENT"))
                .andExpect(jsonPath("$.trackingCode").value("S900.A1"));
        ArgumentCaptor<ShippingCarrier.CreateRequest> sent =
                ArgumentCaptor.forClass(ShippingCarrier.CreateRequest.class);
        verify(carrier, times(1)).createShipment(sent.capture());
        ShippingCarrier.CreateRequest r = sent.getValue();
        String number =
                jdbc.sql("SELECT order_number FROM sales.seller_order")
                        .query(String.class)
                        .single();
        assertThat(r.partnerOrderId()).isEqualTo(number);
        assertThat(r.codAmount()).isEqualTo(225_000); // COD: courier collects items + shipping
        assertThat(r.declaredValue()).isEqualTo(200_000);
        assertThat(r.pickup().phone()).isEqualTo("0901234567");
        assertThat(r.recipient().phone()).isEqualTo("0912345678");
        assertThat(r.recipient().address()).isEqualTo("12 Nguyen Hue");
        assertThat(r.items().stream().mapToInt(ShippingCarrier.Item::weightGrams).sum())
                .isEqualTo(700);

        // Repeating the request does not create a second parcel.
        createShipment(order, 700).andExpect(status().isOk());
        verify(carrier, times(1)).createShipment(any());
        assertThat(count("fulfillment.shipment")).isEqualTo(1);

        // Delivery is reported by the carrier, once, with the right secret.
        webhook("test-hook-secret", "S900.A1", 4, "2026-11-20T10:00:00+07:00")
                .andExpect(jsonPath("$.success").value(true));
        assertThat(orderStatus()).isEqualTo("IN_FULFILLMENT");
        webhook("test-hook-secret", "S900.A1", 5, "2026-11-20T15:00:00+07:00")
                .andExpect(jsonPath("$.success").value(true));
        assertThat(orderStatus()).isEqualTo("DELIVERED");
        assertThat(jdbc.sql("SELECT status FROM fulfillment.shipment").query(String.class).single())
                .isEqualTo("DELIVERED");
        assertThat(
                        jdbc.sql("SELECT return_eligible_until IS NOT NULL FROM sales.seller_order")
                                .query(Boolean.class)
                                .single())
                .isTrue();
        // The same report again changes nothing and adds no event.
        webhook("test-hook-secret", "S900.A1", 5, "2026-11-20T15:00:00+07:00")
                .andExpect(jsonPath("$.success").value(true));
        assertThat(count("fulfillment.shipment_event")).isEqualTo(2);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM sales.order_status_history WHERE to_status = 'DELIVERED'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void aWrongSecretOrUnknownParcelNeverMovesAnOrder() throws Exception {
        String order = readyOrder();
        savePickup();
        doReturn(created("S901.B2")).when(carrier).createShipment(any());
        createShipment(order, 500).andExpect(status().isOk());
        webhook("wrong-secret", "S901.B2", 5, "2026-11-20T15:00:00+07:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(
                        post("/api/v1/shipping/ghtk/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label_id\":\"S901.B2\",\"status_id\":5}"))
                .andExpect(jsonPath("$.success").value(false));
        webhook("test-hook-secret", "NOSUCH.PARCEL", 5, "2026-11-20T15:00:00+07:00")
                .andExpect(jsonPath("$.success").value(true));
        assertThat(orderStatus()).isEqualTo("IN_FULFILLMENT");
        assertThat(count("fulfillment.shipment_event")).isZero();
    }

    @Test
    void aRejectedOrAnUnansweredCreateCanBeRetriedWithoutDuplicatingTheParcel() throws Exception {
        String order = readyOrder();
        savePickup();
        doReturn(
                        new ShippingCarrier.CreateResult(
                                ShippingCarrier.Outcome.REJECTED, null, 0, 0, "Dia chi sai"))
                .when(carrier)
                .createShipment(any());
        createShipment(order, 700)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARRIER_REJECTED"));
        assertThat(orderStatus()).isEqualTo("READY_TO_SHIP");
        assertThat(jdbc.sql("SELECT status FROM fulfillment.shipment").query(String.class).single())
                .isEqualTo("FAILED");

        // No answer at all: the outcome is unknown, so the shipment stays CREATING.
        doReturn(
                        new ShippingCarrier.CreateResult(
                                ShippingCarrier.Outcome.UNKNOWN, null, 0, 0, "timeout"))
                .when(carrier)
                .createShipment(any());
        createShipment(order, 700)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CARRIER_UNAVAILABLE"));
        assertThat(jdbc.sql("SELECT status FROM fulfillment.shipment").query(String.class).single())
                .isEqualTo("CREATING");

        // The retry finds the carrier already has it (duplicate, no label) and looks it up.
        doReturn(
                        new ShippingCarrier.CreateResult(
                                ShippingCarrier.Outcome.DUPLICATE, null, 0, 0, "exists"))
                .when(carrier)
                .createShipment(any());
        doReturn("S902.C3").when(carrier).lookup(anyString());
        createShipment(order, 700)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("S902.C3"));
        assertThat(count("fulfillment.shipment")).isEqualTo(1);
        // The same partner order id was used throughout.
        var captor = ArgumentCaptor.forClass(ShippingCarrier.CreateRequest.class);
        verify(carrier, times(3)).createShipment(captor.capture());
        assertThat(
                        captor.getAllValues().stream()
                                .map(ShippingCarrier.CreateRequest::partnerOrderId)
                                .distinct())
                .hasSize(1);
    }

    @Test
    void cancelingTheParcelReopensTheOrderAndANewParcelGetsANewPartnerId() throws Exception {
        String order = readyOrder();
        savePickup();
        doReturn(created("S903.D4")).when(carrier).createShipment(any());
        createShipment(order, 700).andExpect(status().isOk());
        doReturn(new ShippingCarrier.CancelResult(false, "da lay hang"))
                .when(carrier)
                .cancel(anyString());
        mvc.perform(post(BASE + shop + "/orders/" + order + "/shipment/cancel").with(seller()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARRIER_REJECTED"));
        assertThat(orderStatus()).isEqualTo("IN_FULFILLMENT");

        doReturn(new ShippingCarrier.CancelResult(true, "")).when(carrier).cancel(anyString());
        mvc.perform(post(BASE + shop + "/orders/" + order + "/shipment/cancel").with(seller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_SHIP"))
                .andExpect(jsonPath("$.trackingCode").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbc.sql("SELECT status FROM fulfillment.shipment").query(String.class).single())
                .isEqualTo("CANCELED");

        doReturn(created("S904.E5")).when(carrier).createShipment(any());
        createShipment(order, 700).andExpect(status().isOk());
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM fulfillment.shipment WHERE partner_order_id LIKE '%-2'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        // The carrier canceling on its side reopens the order too.
        webhook("test-hook-secret", "S904.E5", -1, "2026-11-20T16:00:00+07:00")
                .andExpect(jsonPath("$.success").value(true));
        assertThat(orderStatus()).isEqualTo("READY_TO_SHIP");
    }

    @Test
    void onlyTheOwnerOfTheShopCanUseTheShippingApi() throws Exception {
        String order = readyOrder();
        mvc.perform(get(BASE + shop + "/pickup-address").with(seller("someone-else")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put(BASE + shop + "/pickup-address")
                                .with(seller("someone-else"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PICKUP))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(BASE + shop + "/orders/" + order + "/shipment")
                                .with(seller("someone-else"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"weightGrams\":700}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE + shop + "/pickup-address")).andExpect(status().isUnauthorized());
        verify(carrier, never()).createShipment(any());
    }

    // ------------------------------------------------------------------ helpers

    private static final String PICKUP =
            "{\"contactName\":\"Kho Ship Shop\",\"phone\":\"0901234567\",\"street\":\"99 Le Loi\",\"ward\":\"Ben Thanh\",\"district\":\"Quan 1\",\"province\":\"Ho Chi Minh\"}";

    private static ShippingCarrier.CreateResult created(String label) {
        return new ShippingCarrier.CreateResult(
                ShippingCarrier.Outcome.CREATED, label, 22_000, 1_000, "");
    }

    private void savePickup() throws Exception {
        mvc.perform(
                        put(BASE + shop + "/pickup-address")
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PICKUP))
                .andExpect(status().isOk());
    }

    private ResultActions createShipment(String order, int grams) throws Exception {
        return mvc.perform(
                post(BASE + shop + "/orders/" + order + "/shipment")
                        .with(seller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightGrams\":" + grams + "}"));
    }

    private ResultActions webhook(String hash, String label, int statusId, String at)
            throws Exception {
        return mvc.perform(
                post("/api/v1/shipping/ghtk/webhook")
                        .param("hash", hash)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"label_id\":\""
                                        + label
                                        + "\",\"partner_id\":\"x\",\"status_id\":"
                                        + statusId
                                        + ",\"action_time\":\""
                                        + at
                                        + "\",\"reason_code\":\"\",\"reason\":\"\"}"));
    }

    /**
     * A COD order of 2 x 100.000 (+25.000 shipping) placed by a buyer, still waiting for the shop.
     */
    private String placedOrder() throws Exception {
        UUID address = existingAddress();
        mvc.perform(
                        post("/api/v1/cart/lines")
                                .with(buyer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"skuId\":\"" + sku + "\",\"quantity\":2}"))
                .andExpect(status().isOk());
        JsonNode quote =
                body(
                        mvc.perform(
                                        post("/api/v1/checkout/quotes")
                                                .with(buyer())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"addressId\":\"" + address + "\"}"))
                                .andExpect(status().isCreated()));
        JsonNode group =
                body(
                        mvc.perform(
                                        post("/api/v1/checkout/orders")
                                                .with(buyer())
                                                .header(
                                                        "Idempotency-Key",
                                                        "ship-" + UUID.randomUUID())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"quoteId\":\""
                                                                + quote.get("id").asText()
                                                                + "\",\"paymentMethod\":\"COD\"}"))
                                .andExpect(status().isCreated()));
        return group.get("orders").get(0).get("id").asText();
    }

    private String readyOrder() throws Exception {
        String id = placedOrder();
        for (String step : new String[] {"confirm", "pack", "ready"}) {
            mvc.perform(post(BASE + shop + "/orders/" + id + "/" + step).with(seller()))
                    .andExpect(status().isOk());
        }
        return id;
    }

    private UUID existingAddress() throws Exception {
        var found =
                jdbc.sql(
                                "SELECT a.id FROM identity.address a JOIN identity.account c ON c.id=a.account_id WHERE c.external_subject='ship-buyer'")
                        .query(UUID.class)
                        .list();
        if (!found.isEmpty()) {
            return found.get(0);
        }
        JsonNode created =
                body(
                        mvc.perform(
                                        post("/api/v1/me/addresses")
                                                .with(buyer())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"recipientName\":\"Nguyen Van A\",\"phone\":\"0912345678\",\"street\":\"12 Nguyen Hue\",\"ward\":\"Ben Nghe\",\"province\":\"Ho Chi Minh\"}"))
                                .andExpect(status().isCreated()));
        return UUID.fromString(created.get("id").asText());
    }

    private String orderStatus() {
        return jdbc.sql("SELECT status FROM sales.seller_order").query(String.class).single();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JwtRequestPostProcessor buyer() {
        return jwt().jwt(j -> j.subject("ship-buyer"))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    private static JwtRequestPostProcessor seller() {
        return seller(SELLER);
    }

    private static JwtRequestPostProcessor seller(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }
}
