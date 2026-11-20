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
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.service.OutboxProcessingService;
import vn.vshop.modules.platform.service.PolicyService;

/** In-app notifications derived from outbox events: who is told what, once, and nothing private. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.payment.payos.client-id=",
            "vshop.payment.payos.api-key=",
            "vshop.payment.payos.checksum-key=",
            "vshop.payment.vnpay.tmn-code=",
            "vshop.payment.vnpay.hash-secret="
        })
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SELLER = "notif-seller";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PolicyService policies;
    @Autowired OutboxProcessingService outbox;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID shop;
    private UUID sku;

    @BeforeEach
    void seed() {
        UUID category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Notif Test',:slug)")
                .param("id", category)
                .param("slug", "notif-" + category)
                .update();
        shop = UUID.randomUUID();
        jdbc.sql(
                        "WITH a AS (INSERT INTO identity.account(external_subject) VALUES (:o) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,a.id,'Notif Shop','Notification test shop description','APPROVED' FROM a")
                .param("o", SELLER)
                .param("id", shop)
                .update();
        sku = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Notif product','Notification test product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "notif-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,100000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "NOT-" + sku)
                .update();
        UUID warehouse = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:shop,'W1','Kho')")
                .param("id", warehouse)
                .param("shop", shop)
                .update();
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand) VALUES (:sku,:wh,:shop,20)")
                .param("sku", sku)
                .param("wh", warehouse)
                .param("shop", shop)
                .update();
        for (var entry :
                Map.of(
                                PolicyType.SHIPPING_FLAT, Map.of("amount", 25_000L),
                                PolicyType.RETURN, Map.of("windowDays", 7L),
                                PolicyType.COD_LIMITS,
                                        Map.of("maxOrderAmount", 5_000_000L, "maxOpenOrders", 9L),
                                PolicyType.PAYMENT_WINDOW,
                                        Map.of(
                                                "paymentMinutes",
                                                15L,
                                                "reservationBufferMinutes",
                                                5L),
                                PolicyType.RETURN_HANDLING, Map.of("sellerResponseHours", 48L))
                        .entrySet()) {
            policies.approve(
                    "notif-admin",
                    policies.createDraft("notif-admin", entry.getKey(), entry.getValue()).id());
        }
    }

    @Test
    void anOrderLifecycleNotifiesTheRightPersonOnceAndNeverLeaksContactDetails() throws Exception {
        String order = placeCod("buyer-1");
        outbox.receive();
        // A new COD order tells the shop, not the buyer.
        assertThat(titles(SELLER, true)).containsExactly("Đơn mới " + orderNumber(order));
        assertThat(titles("buyer-1", false)).isEmpty();

        // Running the consumer again, or re-delivering the same events, adds nothing.
        outbox.receive();
        jdbc.sql(
                        "UPDATE platform.outbox_event SET processed_at = NULL WHERE event_type = 'OrderPlaced'")
                .update();
        outbox.receive();
        assertThat(titles(SELLER, true)).hasSize(1);

        String base = "/api/v1/seller/shops/" + shop + "/orders/" + order;
        mvc.perform(post(base + "/confirm").with(seller())).andExpect(status().isOk());
        mvc.perform(post(base + "/pack").with(seller())).andExpect(status().isOk());
        mvc.perform(post(base + "/ready").with(seller())).andExpect(status().isOk());
        mvc.perform(
                        post(base + "/ship")
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"trackingCode\":\"NOTIF-0001\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/delivered").with(seller())).andExpect(status().isOk());
        outbox.receive();
        assertThat(titles("buyer-1", false))
                .containsExactlyInAnyOrder(
                        "Người bán đã xác nhận đơn", "Đơn đang được giao", "Đơn đã giao");

        // Return case: the shop hears about the request, the buyer about the answer.
        mvc.perform(
                        post("/api/v1/orders/" + order + "/return-case")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"reasonCode\":\"DAMAGED\",\"preferredResolution\":\"REFUND_ONLY\",\"description\":\"Hộp bị móp, sản phẩm nứt\"}"))
                .andExpect(status().isCreated());
        String caseId =
                jdbc.sql("SELECT id::text FROM aftersales.return_case")
                        .query(String.class)
                        .single();
        mvc.perform(
                        post("/api/v1/seller/shops/"
                                        + shop
                                        + "/return-cases/"
                                        + caseId
                                        + "/offer-refund")
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":50000}"))
                .andExpect(status().isOk());
        outbox.receive();
        assertThat(titles(SELLER, false)).contains("Yêu cầu trả hàng mới");
        assertThat(titles("buyer-1", false)).contains("Người bán đề xuất hoàn tiền");
        // Buying COD: the buyer accepts, the shop is told, a refund needs the buyer's account.
        mvc.perform(post("/api/v1/return-cases/" + caseId + "/accept-offer").with(buyer("buyer-1")))
                .andExpect(status().isOk());
        outbox.receive();
        assertThat(titles(SELLER, false)).contains("Người mua đồng ý hoàn tiền");
        assertThat(titles("buyer-1", false)).contains("Cần thông tin nhận hoàn tiền");

        // Nothing private is ever put into a notification.
        List<String> text =
                jdbc.sql(
                                "SELECT title || ' ' || body || ' ' || coalesce(link, '') FROM platform.notification")
                        .query(String.class)
                        .list();
        assertThat(String.join(" ", text))
                .doesNotContain("0912345678")
                .doesNotContain("Nguyen Hue")
                .doesNotContain("Nguyen Van A");
    }

    @Test
    void buyersAndShopsReadMarkAndCountOnlyTheirOwn() throws Exception {
        String order = placeCod("buyer-1");
        outbox.receive();
        mvc.perform(get("/api/v1/me/notifications/unread-count").with(seller()))
                .andExpect(jsonPath("$.unread").value(1));
        mvc.perform(get("/api/v1/me/notifications/unread-count").with(buyer("buyer-1")))
                .andExpect(jsonPath("$.unread").value(0));
        String id =
                JSON.readTree(
                                mvc.perform(get("/api/v1/me/notifications").with(seller()))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("items")
                        .get(0)
                        .get("id")
                        .asText();
        mvc.perform(get("/api/v1/me/notifications").with(seller()))
                .andExpect(jsonPath("$.items[0].link").value("/seller/orders"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.total").value(1));
        // Somebody else cannot mark it; the owner can, and unread drops.
        mvc.perform(post("/api/v1/me/notifications/" + id + "/read").with(seller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/v1/me/notifications?unread=true").with(seller()))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());

        // The buyer cancels: the shop is told; read-all clears the badge.
        mvc.perform(
                        post("/api/v1/orders/" + order + "/cancel")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Tôi đổi ý không mua nữa\"}"))
                .andExpect(status().isOk());
        outbox.receive();
        mvc.perform(get("/api/v1/me/notifications/unread-count").with(seller()))
                .andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post("/api/v1/me/notifications/read-all").with(seller()))
                .andExpect(jsonPath("$.unread").value(0));
        // Events that name no order are ignored without error.
        jdbc.sql(
                        "INSERT INTO platform.outbox_event (id, event_type, aggregate_type, aggregate_id, payload) VALUES (gen_random_uuid(), 'SHOP_APPROVED', 'audit', gen_random_uuid(), '{\"shopId\":\"x\"}')")
                .update();
        outbox.receive();
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.outbox_event WHERE processed_at IS NULL")
                                .query(Long.class)
                                .single())
                .isZero();
        // Somebody else cannot mark it (last, because the failed call marks the test transaction).
        mvc.perform(post("/api/v1/me/notifications/" + id + "/read").with(buyer("buyer-2")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    private List<String> titles(String subject, boolean sellerRole) throws Exception {
        JsonNode items =
                JSON.readTree(
                                mvc.perform(
                                                get("/api/v1/me/notifications?size=50")
                                                        .with(
                                                                sellerRole
                                                                        ? seller()
                                                                        : buyer(subject)))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("items");
        List<String> out = new java.util.ArrayList<>();
        items.forEach(n -> out.add(n.get("title").asText()));
        return out;
    }

    private String orderNumber(String orderId) {
        return jdbc.sql("SELECT order_number FROM sales.seller_order WHERE id = :id::uuid")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private String placeCod(String subject) throws Exception {
        UUID address = address(subject);
        mvc.perform(
                        post("/api/v1/cart/lines")
                                .with(buyer(subject))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"skuId\":\"" + sku + "\",\"quantity\":2}"))
                .andExpect(status().isOk());
        JsonNode quote =
                body(
                        mvc.perform(
                                        post("/api/v1/checkout/quotes")
                                                .with(buyer(subject))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"addressId\":\"" + address + "\"}"))
                                .andExpect(status().isCreated()));
        JsonNode group =
                body(
                        mvc.perform(
                                        post("/api/v1/checkout/orders")
                                                .with(buyer(subject))
                                                .header(
                                                        "Idempotency-Key",
                                                        "notif-" + UUID.randomUUID())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"quoteId\":\""
                                                                + quote.get("id").asText()
                                                                + "\",\"paymentMethod\":\"COD\"}"))
                                .andExpect(status().isCreated()));
        return group.get("orders").get(0).get("id").asText();
    }

    private UUID address(String subject) throws Exception {
        var existing =
                jdbc.sql(
                                "SELECT a.id FROM identity.address a JOIN identity.account c ON c.id=a.account_id WHERE c.external_subject=:s")
                        .param("s", subject)
                        .query(UUID.class)
                        .list();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        JsonNode created =
                body(
                        mvc.perform(
                                        post("/api/v1/me/addresses")
                                                .with(buyer(subject))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"recipientName\":\"Nguyen Van A\",\"phone\":\"0912345678\",\"street\":\"12 Nguyen Hue\",\"ward\":\"Ben Nghe\",\"province\":\"Ho Chi Minh\"}"))
                                .andExpect(status().isCreated()));
        return UUID.fromString(created.get("id").asText());
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JwtRequestPostProcessor buyer(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    private static JwtRequestPostProcessor seller() {
        return jwt().jwt(j -> j.subject(SELLER))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }
}
