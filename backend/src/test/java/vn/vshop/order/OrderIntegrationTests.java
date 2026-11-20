package vn.vshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false"
        })
@AutoConfigureMockMvc
@Transactional
class OrderIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID shopA;
    private UUID shopB;
    private UUID category;
    private UUID skuA1; // shop A, 100.000, stock 10
    private UUID skuB1; // shop B, 40.000, stock 5

    @BeforeEach
    void seed() {
        category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Order Test',:slug)")
                .param("id", category)
                .param("slug", "order-" + category)
                .update();
        shopA = createShop("order-seller-a", "Shop A");
        shopB = createShop("order-seller-b", "Shop B");
        skuA1 = createSku(shopA, "A1", 100_000, 10);
        skuB1 = createSku(shopB, "B1", 40_000, 5);
    }

    // ---------------------------------------------------------------- policies

    @Test
    void checkoutStaysBlockedUntilAllFourPoliciesAreApprovedAndDraftsAreNeverUsed()
            throws Exception {
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, 1);
        JsonNode quote = quote("buyer-1", address);
        // No policy at all: the quote is calculated but shipping is explicitly not.
        assertThat(quote.get("content").get("notCalculated").toString()).contains("SHIPPING");
        assertThat(quote.get("content").get("shippingTotal").asLong()).isZero();
        place("buyer-1", quote, "gate-key-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_POLICY_MISSING"));

        Map<String, UUID> drafts = new java.util.LinkedHashMap<>();
        drafts.put("SHIPPING_FLAT", draft("SHIPPING_FLAT", Map.of("amount", 30_000)));
        drafts.put("RETURN", draft("RETURN", Map.of("windowDays", 7)));
        drafts.put(
                "COD_LIMITS",
                draft("COD_LIMITS", Map.of("maxOrderAmount", 5_000_000, "maxOpenOrders", 5)));
        drafts.put(
                "PAYMENT_WINDOW",
                draft(
                        "PAYMENT_WINDOW",
                        Map.of("paymentMinutes", 15, "reservationBufferMinutes", 5)));
        // Drafts are not rules.
        place("buyer-1", quote, "gate-key-0002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_POLICY_MISSING"));
        assertThat(quote("buyer-1", address).get("content").get("notCalculated").toString())
                .contains("SHIPPING");

        approve(drafts.get("SHIPPING_FLAT"));
        approve(drafts.get("RETURN"));
        approve(drafts.get("COD_LIMITS"));
        place("buyer-1", quote, "gate-key-0003")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_POLICY_MISSING"))
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("PAYMENT_WINDOW")));
        approve(drafts.get("PAYMENT_WINDOW"));

        // Shipping now exists, which changes the totals, so the old quote is stale.
        place("buyer-1", quote, "gate-key-0004")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_STALE"));
        JsonNode fresh = quote("buyer-1", address);
        assertThat(fresh.get("content").get("shippingTotal").asLong()).isEqualTo(30_000);
        assertThat(fresh.get("content").get("payableTotal").asLong()).isEqualTo(130_000);
        assertThat(fresh.get("content").get("notCalculated").toString()).doesNotContain("SHIPPING");
        place("buyer-1", fresh, "gate-key-0005").andExpect(status().isCreated());
    }

    @Test
    void policyEndpointsNeedAnAdminValidateContentAndKeepAHistory() throws Exception {
        String body = "{\"policyType\":\"SHIPPING_FLAT\",\"content\":{\"amount\":20000}}";
        mvc.perform(
                        post("/api/v1/admin/policies")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        // Unknown field, missing field, out of range.
        for (String bad :
                List.of(
                        "{\"policyType\":\"SHIPPING_FLAT\",\"content\":{\"amount\":20000,\"x\":1}}",
                        "{\"policyType\":\"COD_LIMITS\",\"content\":{\"maxOrderAmount\":1000}}",
                        "{\"policyType\":\"SHIPPING_FLAT\",\"content\":{\"amount\":-1}}",
                        "{\"policyType\":\"RETURN\",\"content\":{\"windowDays\":9999}}",
                        "{\"policyType\":\"NOPE\",\"content\":{\"amount\":1}}")) {
            mvc.perform(
                            post("/api/v1/admin/policies")
                                    .with(admin())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(bad))
                    .andExpect(status().isBadRequest());
        }
        UUID first = draft("SHIPPING_FLAT", Map.of("amount", 20_000));
        mvc.perform(get("/api/v1/admin/policies").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + first + "')].status").value("DRAFT"));
        approve(first);
        mvc.perform(post("/api/v1/admin/policies/" + first + "/approve").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_POLICY_STATE"));
        UUID second = draft("SHIPPING_FLAT", Map.of("amount", 25_000));
        approve(second);
        mvc.perform(get("/api/v1/admin/policies").with(admin()))
                .andExpect(jsonPath("$[?(@.id=='" + first + "')].status").value("RETIRED"))
                .andExpect(jsonPath("$[?(@.id=='" + second + "')].status").value("APPROVED"))
                .andExpect(jsonPath("$[?(@.id=='" + second + "')].version").value(2));
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE action IN ('POLICY_DRAFTED','POLICY_APPROVED') AND resource_id IN (:a,:b)")
                                .param("a", first)
                                .param("b", second)
                                .query(Long.class)
                                .single())
                .isEqualTo(4);
        mvc.perform(post("/api/v1/admin/policies/" + UUID.randomUUID() + "/approve").with(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- placing

    @Test
    void placesOneCodOrderPerShopWithSnapshotsConsumedStockAndAnEmptyCart() throws Exception {
        approveAll(30_000, 7, 5_000_000, 5);
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, 3);
        add("buyer-1", skuB1, 2);
        JsonNode quote = quote("buyer-1", address);
        assertThat(quote.get("content").get("payableTotal").asLong())
                .isEqualTo(300_000 + 80_000 + 2 * 30_000);

        JsonNode group =
                body(place("buyer-1", quote, "place-key-0001").andExpect(status().isCreated()));
        assertThat(group.get("paymentMethod").asText()).isEqualTo("COD");
        assertThat(group.get("totalAmount").asLong()).isEqualTo(440_000);
        assertThat(group.get("orders")).hasSize(2);
        long sum = 0;
        for (JsonNode order : group.get("orders")) {
            sum += order.get("payableAmount").asLong();
            assertThat(order.get("status").asText()).isEqualTo("AWAITING_SELLER");
            assertThat(order.get("orderNumber").asText()).matches("VS\\d{6}-[A-Z2-9]{8}");
            assertThat(order.get("shippingAmount").asLong()).isEqualTo(30_000);
            assertThat(order.get("recipient").get("phone").asText()).isEqualTo("0912345678");
            assertThat(order.get("history")).hasSize(1);
            assertThat(order.get("history").get(0).get("toStatus").asText())
                    .isEqualTo("AWAITING_SELLER");
            assertThat(order.get("history").get(0).get("fromStatus").isNull()).isTrue();
        }
        assertThat(sum).isEqualTo(440_000);
        JsonNode orderA = orderOfShop(group, "Shop A");
        assertThat(orderA.get("itemAmount").asLong()).isEqualTo(300_000);
        assertThat(orderA.get("lines").get(0).get("productName").asText()).isEqualTo("Product A1");
        assertThat(orderA.get("lines").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(orderA.get("lines").get(0).get("unitPrice").asLong()).isEqualTo(100_000);

        // COD consumes the stock at once and every line is linked to a COMMITTED reservation.
        assertThat(onHand(skuA1)).isEqualTo(7);
        assertThat(reserved(skuA1)).isZero();
        assertThat(onHand(skuB1)).isEqualTo(3);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM sales.order_line_reservation r JOIN inventory.stock_reservation s ON s.id = r.reservation_id WHERE s.status = 'COMMITTED'")
                                .query(Long.class)
                                .single())
                .isEqualTo(2);
        // The cart is empty and its version moved on.
        mvc.perform(get("/api/v1/cart").with(buyer("buyer-1")))
                .andExpect(jsonPath("$.itemCount").value(0));
        // The event carries ids and amounts, no personal data.
        List<UUID> placed = new java.util.ArrayList<>();
        group.get("orders").forEach(o -> placed.add(UUID.fromString(o.get("id").asText())));
        List<String> events =
                jdbc.sql(
                                "SELECT payload::text FROM platform.outbox_event WHERE event_type = 'OrderPlaced' AND aggregate_type = 'order' AND aggregate_id IN (:ids)")
                        .param("ids", placed)
                        .query(String.class)
                        .list();
        assertThat(events).hasSize(2);
        assertThat(String.join("", events)).doesNotContain("0912345678").doesNotContain("Nguyen");
        // Recipient is stored encrypted, region readable.
        assertThat(
                        jdbc.sql(
                                        "SELECT recipient_snapshot_ciphertext FROM sales.seller_order LIMIT 1")
                                .query(String.class)
                                .single())
                .startsWith("v1.")
                .doesNotContain("0912345678");

        // Reads: result page, list and detail, scoped to the buyer.
        String groupId = group.get("id").asText();
        mvc.perform(get("/api/v1/checkout/groups/" + groupId).with(buyer("buyer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(2));
        mvc.perform(get("/api/v1/orders").with(buyer("buyer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].itemCount").exists());
        String orderId = orderA.get("id").asText();
        mvc.perform(get("/api/v1/orders/" + orderId).with(buyer("buyer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderA.get("orderNumber").asText()));
        mvc.perform(get("/api/v1/orders/" + orderId).with(buyer("buyer-2")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/checkout/groups/" + groupId).with(buyer("buyer-2")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders").with(buyer("buyer-2")))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aRetryWithTheSameKeyReturnsTheSameOrdersAndNeverCreatesMore() throws Exception {
        approveAll(30_000, 7, 5_000_000, 5);
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, 1);
        JsonNode quote = quote("buyer-1", address);
        JsonNode first =
                body(place("buyer-1", quote, "retry-key-0001").andExpect(status().isCreated()));
        JsonNode second =
                body(place("buyer-1", quote, "retry-key-0001").andExpect(status().isOk()));
        assertThat(second.get("id")).isEqualTo(first.get("id"));
        assertThat(second.get("orders").get(0).get("orderNumber"))
                .isEqualTo(first.get("orders").get(0).get("orderNumber"));
        assertThat(count("sales.seller_order")).isEqualTo(1);
        assertThat(onHand(skuA1)).isEqualTo(9);
        // The same key for a different quote is a client error, not a second order.
        add("buyer-1", skuA1, 1);
        JsonNode other = quote("buyer-1", address);
        place("buyer-1", other, "retry-key-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        // The same quote under a new key was already used.
        place("buyer-1", quote, "retry-key-0002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_STALE"));
        assertThat(count("sales.seller_order")).isEqualTo(1);
    }

    @Test
    void anOrderIsNeverPlacedAtAPriceOrCartTheBuyerHasNotSeen() throws Exception {
        approveAll(30_000, 7, 5_000_000, 5);
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, 1);

        JsonNode cartChanged = quote("buyer-1", address);
        add("buyer-1", skuA1, 1);
        place("buyer-1", cartChanged, "stale-key-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_STALE"));

        JsonNode priceChanged = quote("buyer-1", address);
        jdbc.sql("UPDATE catalog.product_sku SET price = 120000 WHERE id = :id")
                .param("id", skuA1)
                .update();
        place("buyer-1", priceChanged, "stale-key-0002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_STALE"))
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("Giá đã thay đổi")));

        JsonNode expiring = quote("buyer-1", address);
        jdbc.sql(
                        "UPDATE checkout.quote SET created_at = now() - interval '2 seconds', expires_at = now() - interval '1 second' WHERE id = :id")
                .param("id", UUID.fromString(expiring.get("id").asText()))
                .update();
        place("buyer-1", expiring, "stale-key-0003")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_STALE"));

        assertThat(count("sales.seller_order")).isZero();
        assertThat(onHand(skuA1)).isEqualTo(10);
    }

    @Test
    void aQuoteWithoutAddressAndSomeoneElsesQuoteCannotBeOrdered() throws Exception {
        approveAll(30_000, 7, 5_000_000, 5);
        add("buyer-1", skuA1, 1);
        JsonNode noAddress = quote("buyer-1", null);
        place("buyer-1", noAddress, "addr-key-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_ADDRESS_REQUIRED"));
        JsonNode mine = quote("buyer-1", address("buyer-1"));
        place("buyer-2", mine, "other-key-0001")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        assertThat(count("sales.seller_order")).isZero();
    }

    @Test
    void anItemThatSoldOutAfterTheQuoteStopsTheWholeOrderAndChangesNothing() throws Exception {
        approveAll(30_000, 7, 5_000_000, 5);
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, 2);
        add("buyer-1", skuB1, 2);
        JsonNode quote = quote("buyer-1", address);
        jdbc.sql("UPDATE inventory.stock_balance SET on_hand = 1 WHERE sku_id = :id")
                .param("id", skuB1)
                .update();
        place("buyer-1", quote, "stock-key-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_UNAVAILABLE"));
        assertThat(count("sales.seller_order")).isZero();
        assertThat(count("sales.checkout_group")).isZero();
        assertThat(onHand(skuA1)).isEqualTo(10);
        mvc.perform(get("/api/v1/cart").with(buyer("buyer-1")))
                .andExpect(jsonPath("$.itemCount").value(4));
    }

    @Test
    void aShopMemberCannotBuyFromTheirOwnShop() throws Exception {
        approveAll(30_000, 7, 5_000_000, 5);
        jdbc.sql(
                        "INSERT INTO merchant.shop_member(shop_id, account_id, role) SELECT id, owner_account_id, 'OWNER' FROM merchant.shop WHERE id = :id")
                .param("id", shopA)
                .update();
        UUID address = address("order-seller-a");
        add("order-seller-a", skuA1, 1);
        JsonNode quote = quote("order-seller-a", address);
        place("order-seller-a", quote, "self-key-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_PURCHASE_NOT_ALLOWED"));
        assertThat(count("sales.seller_order")).isZero();
    }

    @Test
    void codLimitsBlockLargeOrdersAndTooManyOpenOrders() throws Exception {
        approveAll(30_000, 7, 150_000, 1);
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, 2); // 200.000 + 30.000 > 150.000
        place("buyer-1", quote("buyer-1", address), "limit-key-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COD_LIMIT_EXCEEDED"));
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/api/v1/cart/lines/" + skuA1)
                                .with(buyer("buyer-1")))
                .andExpect(status().isOk());
        add("buyer-1", skuB1, 1); // 40.000 + 30.000 within the limit
        place("buyer-1", quote("buyer-1", address), "limit-key-0002")
                .andExpect(status().isCreated());
        add("buyer-1", skuB1, 1);
        place("buyer-1", quote("buyer-1", address), "limit-key-0003")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COD_LIMIT_EXCEEDED"));
        assertThat(count("sales.seller_order")).isEqualTo(1);
    }

    @Test
    void paymentMethodAndIdempotencyKeyAreValidated() throws Exception {
        approveAll(30_000, 7, 5_000_000, 5);
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, 1);
        JsonNode quote = quote("buyer-1", address);
        mvc.perform(
                        post("/api/v1/checkout/orders")
                                .with(buyer("buyer-1"))
                                .header("Idempotency-Key", "psp-key-00001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"quoteId\":\""
                                                + quote.get("id").asText()
                                                + "\",\"paymentMethod\":\"PSP\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/checkout/orders")
                                .with(buyer("buyer-1"))
                                .header("Idempotency-Key", "short")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"quoteId\":\""
                                                + quote.get("id").asText()
                                                + "\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/checkout/orders")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"quoteId\":\""
                                                + quote.get("id").asText()
                                                + "\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/checkout/orders")
                                .with(buyer("buyer-1"))
                                .header("Idempotency-Key", "no-quote-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/not-a-uuid").with(buyer("buyer-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        assertThat(count("sales.seller_order")).isZero();
    }

    // ---------------------------------------------------------------- fulfilment (S2)

    @Test
    void shopWorksAnOrderToDeliveredAndSeesBuyerDetailsOnlyAfterConfirming() throws Exception {
        JsonNode order = placedOrderOfShopA(2);
        String id = order.get("id").asText();
        String base = "/api/v1/seller/shops/" + shopA + "/orders";

        // Before confirming only the region is visible.
        mvc.perform(get(base + "/" + id).with(seller("order-seller-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_SELLER"))
                .andExpect(jsonPath("$.recipient.name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.recipient.phone").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.recipient.ward").value("Ben Nghe"));
        mvc.perform(get(base).with(seller("order-seller-a")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get(base + "?status=CONFIRMED").with(seller("order-seller-a")))
                .andExpect(jsonPath("$.total").value(0));

        // Steps cannot be skipped.
        for (String skipped : List.of("pack", "ready", "delivered")) {
            mvc.perform(post(base + "/" + id + "/" + skipped).with(seller("order-seller-a")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        }
        mvc.perform(post(base + "/" + id + "/confirm").with(seller("order-seller-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.recipient.phone").value("0912345678"));
        mvc.perform(post(base + "/" + id + "/confirm").with(seller("order-seller-a")))
                .andExpect(status().isConflict());
        mvc.perform(post(base + "/" + id + "/pack").with(seller("order-seller-a")))
                .andExpect(jsonPath("$.status").value("PACKING"));
        mvc.perform(post(base + "/" + id + "/ready").with(seller("order-seller-a")))
                .andExpect(jsonPath("$.status").value("READY_TO_SHIP"));
        for (String bad : List.of("", "ab", "has space", "x".repeat(101))) {
            mvc.perform(
                            post(base + "/" + id + "/ship")
                                    .with(seller("order-seller-a"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"trackingCode\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post(base + "/" + id + "/ship")
                                .with(seller("order-seller-a"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"trackingCode\":\"VN123-456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_FULFILLMENT"))
                .andExpect(jsonPath("$.trackingCode").value("VN123-456"));
        mvc.perform(post(base + "/" + id + "/delivered").with(seller("order-seller-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.history.length()").value(6));
        assertThat(
                        jdbc.sql(
                                        "SELECT delivered_at IS NOT NULL AND return_eligible_until > delivered_at + interval '6 days' AND return_eligible_until < delivered_at + interval '8 days' FROM sales.seller_order WHERE id = :id")
                                .param("id", UUID.fromString(id))
                                .query(Boolean.class)
                                .single())
                .isTrue();
        // The buyer sees the progress, and can no longer cancel.
        mvc.perform(get("/api/v1/orders/" + id).with(buyer("buyer-1")))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        cancel("buyer-1", id, "Tôi đổi ý không mua nữa").andExpect(status().isConflict());
    }

    @Test
    void rejectingOrCancelingPutsTheStockBackExactlyOnce() throws Exception {
        JsonNode rejected = placedOrderOfShopA(3);
        assertThat(onHand(skuA1)).isEqualTo(7);
        String base = "/api/v1/seller/shops/" + shopA + "/orders/" + rejected.get("id").asText();
        mvc.perform(
                        post(base + "/reject")
                                .with(seller("order-seller-a"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"ngắn\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(base + "/reject")
                                .with(seller("order-seller-a"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Hết hàng thực tế ở kho\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.cancelReason").value("Hết hàng thực tế ở kho"));
        assertThat(onHand(skuA1)).isEqualTo(10);
        mvc.perform(
                        post(base + "/reject")
                                .with(seller("order-seller-a"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Hết hàng thực tế ở kho\"}"))
                .andExpect(status().isConflict());
        assertThat(onHand(skuA1)).isEqualTo(10);

        // Buyer cancel before the shop starts.
        JsonNode second = placedOrderOfShopA(4);
        assertThat(onHand(skuA1)).isEqualTo(6);
        String secondId = second.get("id").asText();
        cancel("buyer-1", secondId, "x").andExpect(status().isBadRequest());
        cancel("buyer-2", secondId, "Tôi đổi ý không mua nữa").andExpect(status().isNotFound());
        cancel("buyer-1", secondId, "Tôi đổi ý không mua nữa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("Tôi đổi ý không mua nữa"));
        assertThat(onHand(skuA1)).isEqualTo(10);
        cancel("buyer-1", secondId, "Tôi đổi ý không mua nữa").andExpect(status().isConflict());
        assertThat(onHand(skuA1)).isEqualTo(10);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM inventory.stock_movement WHERE movement_type = 'RETURN' AND sku_id = :s")
                                .param("s", skuA1)
                                .query(Long.class)
                                .single())
                .isEqualTo(2);

        // Once the shop confirmed, the buyer can no longer cancel directly.
        JsonNode third = placedOrderOfShopA(1);
        mvc.perform(
                        post("/api/v1/seller/shops/"
                                        + shopA
                                        + "/orders/"
                                        + third.get("id").asText()
                                        + "/confirm")
                                .with(seller("order-seller-a")))
                .andExpect(status().isOk());
        cancel("buyer-1", third.get("id").asText(), "Tôi đổi ý không mua nữa")
                .andExpect(status().isConflict());
        assertThat(onHand(skuA1)).isEqualTo(9);
    }

    @Test
    void onlyTheOwningShopCanSeeOrChangeItsOrders() throws Exception {
        JsonNode order = placedOrderOfShopA(1);
        String id = order.get("id").asText();
        // Another seller on this shop's path, and the owner on another shop's path.
        mvc.perform(get("/api/v1/seller/shops/" + shopA + "/orders").with(seller("order-seller-b")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/seller/shops/" + shopA + "/orders/" + id + "/confirm")
                                .with(seller("order-seller-b")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v1/seller/shops/" + shopB + "/orders/" + id)
                                .with(seller("order-seller-b")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/seller/shops/" + shopA + "/orders"))
                .andExpect(status().isUnauthorized());
        assertThat(
                        jdbc.sql("SELECT status FROM sales.seller_order WHERE id = :id")
                                .param("id", UUID.fromString(id))
                                .query(String.class)
                                .single())
                .isEqualTo("AWAITING_SELLER");
    }

    @Test
    void ordersNotConfirmedWithinTheApproved24HoursLandInTheSupportQueueAndAreNotCanceled()
            throws Exception {
        JsonNode order = placedOrderOfShopA(1);
        UUID id = UUID.fromString(order.get("id").asText());
        // No deadline policy yet: nothing is overdue, whatever the age.
        jdbc.sql(
                        "UPDATE sales.seller_order SET created_at = now() - interval '30 hours' WHERE id = :id")
                .param("id", id)
                .update();
        mvc.perform(get("/api/v1/admin/orders/overdue").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmMinutes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/admin/orders/overdue").with(buyer("buyer-1")))
                .andExpect(status().isForbidden());

        // A too-short deadline is refused; 24h (1440 minutes) is approved like any policy.
        mvc.perform(
                        post("/api/v1/admin/policies")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"policyType\":\"SELLER_CONFIRMATION\",\"content\":{\"confirmMinutes\":10}}"))
                .andExpect(status().isBadRequest());
        approve(draft("SELLER_CONFIRMATION", Map.of("confirmMinutes", 1440)));
        mvc.perform(get("/api/v1/admin/orders/overdue").with(admin()))
                .andExpect(jsonPath("$.confirmMinutes").value(1440))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].minutesOverdue").value(360));
        // Nothing was canceled and the stock is untouched.
        assertThat(
                        jdbc.sql("SELECT status FROM sales.seller_order WHERE id = :id")
                                .param("id", id)
                                .query(String.class)
                                .single())
                .isEqualTo("AWAITING_SELLER");
        assertThat(onHand(skuA1)).isEqualTo(9);

        // Younger than 24h is not overdue; confirming removes it from the queue.
        jdbc.sql(
                        "UPDATE sales.seller_order SET created_at = now() - interval '23 hours' WHERE id = :id")
                .param("id", id)
                .update();
        mvc.perform(get("/api/v1/admin/orders/overdue").with(admin()))
                .andExpect(jsonPath("$.items.length()").value(0));
        jdbc.sql(
                        "UPDATE sales.seller_order SET created_at = now() - interval '25 hours' WHERE id = :id")
                .param("id", id)
                .update();
        mvc.perform(get("/api/v1/admin/orders/overdue").with(admin()))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(
                        post("/api/v1/seller/shops/" + shopA + "/orders/" + id + "/confirm")
                                .with(seller("order-seller-a")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/orders/overdue").with(admin()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private JsonNode placedOrderOfShopA(int quantity) throws Exception {
        if (jdbc.sql("SELECT count(*) FROM platform.policy_version WHERE status = 'APPROVED'")
                        .query(Long.class)
                        .single()
                < 4) {
            approveAll(30_000, 7, 5_000_000, 5);
        }
        UUID address = address("buyer-1");
        add("buyer-1", skuA1, quantity);
        JsonNode quote = quote("buyer-1", address);
        JsonNode group =
                body(
                        place("buyer-1", quote, "ful-" + UUID.randomUUID())
                                .andExpect(status().isCreated()));
        return group.get("orders").get(0);
    }

    private ResultActions cancel(String subject, String orderId, String reason) throws Exception {
        return mvc.perform(
                post("/api/v1/orders/" + orderId + "/cancel")
                        .with(buyer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}"));
    }

    private static JwtRequestPostProcessor seller(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions place(String subject, JsonNode quote, String key) throws Exception {
        return mvc.perform(
                post("/api/v1/checkout/orders")
                        .with(buyer(subject))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"quoteId\":\""
                                        + quote.get("id").asText()
                                        + "\",\"paymentMethod\":\"COD\"}"));
    }

    private JsonNode quote(String subject, UUID addressId) throws Exception {
        String content = addressId == null ? "{}" : "{\"addressId\":\"" + addressId + "\"}";
        return body(
                mvc.perform(
                                post("/api/v1/checkout/quotes")
                                        .with(buyer(subject))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(content))
                        .andExpect(status().isCreated()));
    }

    private UUID address(String subject) throws Exception {
        JsonNode created =
                body(
                        mvc.perform(
                                        post("/api/v1/me/addresses")
                                                .with(buyer(subject))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        """
                                                        {"recipientName":"Nguyen Van A","phone":"0912345678",
                                                         "street":"12 Nguyen Hue","ward":"Ben Nghe",
                                                         "province":"Ho Chi Minh"}
                                                        """))
                                .andExpect(status().isCreated()));
        return UUID.fromString(created.get("id").asText());
    }

    private JsonNode add(String subject, UUID skuId, int quantity) throws Exception {
        return body(
                mvc.perform(
                                post("/api/v1/cart/lines")
                                        .with(buyer(subject))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"skuId\":\""
                                                        + skuId
                                                        + "\",\"quantity\":"
                                                        + quantity
                                                        + "}"))
                        .andExpect(status().isOk()));
    }

    private UUID draft(String type, Map<String, Integer> content) throws Exception {
        JsonNode created =
                body(
                        mvc.perform(
                                        post("/api/v1/admin/policies")
                                                .with(admin())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"policyType\":\""
                                                                + type
                                                                + "\",\"content\":"
                                                                + JSON.writeValueAsString(content)
                                                                + "}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("DRAFT")));
        return UUID.fromString(created.get("id").asText());
    }

    private void approve(UUID id) throws Exception {
        mvc.perform(post("/api/v1/admin/policies/" + id + "/approve").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private void approveAll(int fee, int returnDays, int maxOrder, int maxOpen) throws Exception {
        approve(draft("SHIPPING_FLAT", Map.of("amount", fee)));
        approve(draft("RETURN", Map.of("windowDays", returnDays)));
        approve(draft("COD_LIMITS", Map.of("maxOrderAmount", maxOrder, "maxOpenOrders", maxOpen)));
        approve(
                draft(
                        "PAYMENT_WINDOW",
                        Map.of("paymentMinutes", 15, "reservationBufferMinutes", 5)));
    }

    private static JsonNode orderOfShop(JsonNode group, String shopName) {
        for (JsonNode order : group.get("orders")) {
            if (shopName.equals(order.get("shopName").asText())) {
                return order;
            }
        }
        throw new AssertionError("no order for " + shopName);
    }

    private long onHand(UUID sku) {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(on_hand),0) FROM inventory.stock_balance WHERE sku_id = :s")
                .param("s", sku)
                .query(Long.class)
                .single();
    }

    private long reserved(UUID sku) {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(reserved),0) FROM inventory.stock_balance WHERE sku_id = :s")
                .param("s", sku)
                .query(Long.class)
                .single();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JwtRequestPostProcessor buyer(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(
                        j -> {
                            j.subject("order-test-admin");
                            j.claim("amr", List.of("pwd", "otp"));
                        })
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private UUID createShop(String owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "WITH account AS (INSERT INTO identity.account(external_subject) VALUES (:o) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,account.id,:name,'Order test shop description only','APPROVED' FROM account")
                .param("o", owner)
                .param("id", id)
                .param("name", name)
                .update();
        return id;
    }

    /** Creates a single-SKU product; the product row id doubles as the SKU id. */
    private UUID createSku(UUID shop, String code, long price, long stock) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,:name,'Order test product description','lamp','ACTIVE')")
                .param("id", id)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "order-" + id)
                .param("name", "Product " + code)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price,status) VALUES (:id,:id,:shop,:code,:price,'ACTIVE')")
                .param("id", id)
                .param("shop", shop)
                .param("code", code + "-" + id)
                .param("price", price)
                .update();
        UUID warehouse = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:shop,:code,'Kho')")
                .param("id", warehouse)
                .param("shop", shop)
                .param("code", "W-" + id)
                .update();
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand) VALUES (:sku,:wh,:shop,:q)")
                .param("sku", id)
                .param("wh", warehouse)
                .param("shop", shop)
                .param("q", stock)
                .update();
        return id;
    }
}
