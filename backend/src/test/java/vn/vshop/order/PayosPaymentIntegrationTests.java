package vn.vshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import vn.vshop.modules.order.gateway.PaymentGateway;
import vn.vshop.modules.order.gateway.PayosGateway;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.service.PolicyService;

/** payOS: the payment page is created over the network after commit; signed webhooks pay. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.payment.provider=payos",
            "vshop.payment.payos.client-id=test-client",
            "vshop.payment.payos.api-key=test-api-key",
            "vshop.payment.payos.checksum-key=unit-test-checksum-key",
            "vshop.payment.return-base-url=https://shop.example.test"
        })
@AutoConfigureMockMvc
@Transactional
class PayosPaymentIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KEY = "unit-test-checksum-key";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PolicyService policies;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoSpyBean PayosGateway gateway;

    private UUID sku;

    @BeforeEach
    void seed() {
        UUID category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Payos Test',:slug)")
                .param("id", category)
                .param("slug", "payos-" + category)
                .update();
        UUID shop = UUID.randomUUID();
        jdbc.sql(
                        "WITH a AS (INSERT INTO identity.account(external_subject) VALUES ('payos-seller') ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,a.id,'Payos Shop','Payos test shop description','APPROVED' FROM a")
                .param("id", shop)
                .update();
        sku = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Payos product','Payos test product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "payos-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,100000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "PAYOS-" + sku)
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
                    "payos-admin",
                    policies.createDraft("payos-admin", entry.getKey(), entry.getValue()).id());
        }
    }

    @Test
    void thePaymentPageIsCreatedAfterCommitWithAUniqueIntegerCodeAndSignedWebhookPays()
            throws Exception {
        doReturn(page("https://pay.payos.example/web/abc", "link-1"))
                .when(gateway)
                .createSession(any());
        JsonNode group = placeOnline("buyer-1", 2, "payos-key-0001", 201);
        assertThat(group.get("paymentUrl").asText()).isEqualTo("https://pay.payos.example/web/abc");
        assertThat(group.get("orders").get(0).get("status").asText()).isEqualTo("PENDING_PAYMENT");
        ArgumentCaptor<PaymentGateway.Session> session =
                ArgumentCaptor.forClass(PaymentGateway.Session.class);
        verify(gateway, times(1)).createSession(session.capture());
        long orderCode = Long.parseLong(session.getValue().merchantReference());
        assertThat(orderCode).isGreaterThanOrEqualTo(10_000_000L);
        assertThat(session.getValue().amountVnd()).isEqualTo(225_000);
        assertThat(session.getValue().returnUrl())
                .isEqualTo("https://shop.example.test/checkout/result/" + group.get("id").asText());
        assertThat(reserved()).isEqualTo(2);

        // Asking again (retry, page refresh) returns the stored page without calling payOS again.
        mvc.perform(
                        get("/api/v1/checkout/groups/" + group.get("id").asText())
                                .with(buyer("buyer-1")))
                .andExpect(jsonPath("$.paymentUrl").value("https://pay.payos.example/web/abc"));
        verify(gateway, times(1)).createSession(any());

        // The signed notification confirms the order exactly once.
        Map<String, Object> hook = webhook(orderCode, 225_000, "TXN9001");
        send(hook).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00"));
        assertThat(orderStatus()).isEqualTo("AWAITING_SELLER");
        assertThat(onHand()).isEqualTo(8);
        assertThat(reserved()).isZero();
        assertThat(count("payment.charge")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM payment.intent").query(String.class).single())
                .isEqualTo("PAID");
        send(hook).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("02"));
        assertThat(count("payment.charge")).isEqualTo(1);
        assertThat(onHand()).isEqualTo(8);
    }

    @Test
    void forgedWrongAmountAndFailedNotificationsDoNothing() throws Exception {
        doReturn(page("https://pay.payos.example/web/def", "link-2"))
                .when(gateway)
                .createSession(any());
        JsonNode group = placeOnline("buyer-1", 1, "payos-key-0002", 201);
        ArgumentCaptor<PaymentGateway.Session> session =
                ArgumentCaptor.forClass(PaymentGateway.Session.class);
        verify(gateway).createSession(session.capture());
        long orderCode = Long.parseLong(session.getValue().merchantReference());

        Map<String, Object> forged = webhook(orderCode, 125_000, "TXN9002");
        forged.put("signature", "0".repeat(64));
        send(forged).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
        @SuppressWarnings("unchecked")
        Map<String, Object> tampered = (Map<String, Object>) webhook(orderCode, 125_000, "TXN9003");
        ((Map<String, Object>) tampered.get("data")).put("amount", 1); // changed after signing
        send(tampered).andExpect(jsonPath("$.success").value(false));
        send(webhook(orderCode, 100_000, "TXN9004")).andExpect(jsonPath("$.code").value("04"));
        send(webhook(999_999_999L, 125_000, "TXN9005")).andExpect(jsonPath("$.code").value("01"));
        // A report that the payment did not succeed.
        Map<String, Object> failed = webhook(orderCode, 125_000, "TXN9006", "01", false);
        send(failed).andExpect(status().isOk());
        assertThat(orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(count("payment.charge")).isZero();
        assertThat(group.get("id").asText()).isNotBlank();
    }

    @Test
    void anUnreachableProviderLeavesTheOrderWaitingAndTheNextRequestGetsAPage() throws Exception {
        doReturn(
                        new PaymentGateway.SessionResult(
                                PaymentGateway.Outcome.FAILED, null, null, "timeout"))
                .when(gateway)
                .createSession(any());
        JsonNode group = placeOnline("buyer-1", 1, "payos-key-0003", 201);
        assertThat(group.get("paymentUrl").isNull()).isTrue();
        assertThat(group.get("orders").get(0).get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(count("payment.attempt")).isEqualTo(1);

        // The provider is back: reading the group again creates the page for the same attempt.
        doReturn(page("https://pay.payos.example/web/ghi", "link-3"))
                .when(gateway)
                .createSession(any());
        mvc.perform(
                        get("/api/v1/checkout/groups/" + group.get("id").asText())
                                .with(buyer("buyer-1")))
                .andExpect(jsonPath("$.paymentUrl").value("https://pay.payos.example/web/ghi"));
        assertThat(count("payment.attempt")).isEqualTo(1);
    }

    @Test
    void aReferenceThePaymentProviderAlreadyHasGetsAFreshAttempt() throws Exception {
        doReturn(
                        new PaymentGateway.SessionResult(
                                PaymentGateway.Outcome.DUPLICATE, null, null, "exists"))
                .doReturn(page("https://pay.payos.example/web/jkl", "link-4"))
                .when(gateway)
                .createSession(any());
        JsonNode group = placeOnline("buyer-1", 1, "payos-key-0004", 201);
        assertThat(group.get("paymentUrl").asText()).isEqualTo("https://pay.payos.example/web/jkl");
        assertThat(count("payment.attempt")).isEqualTo(2);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(DISTINCT merchant_attempt_reference) FROM payment.attempt")
                                .query(Long.class)
                                .single())
                .isEqualTo(2);
        assertThat(
                        jdbc.sql("SELECT count(*) FROM payment.attempt WHERE status = 'EXPIRED'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void theSignatureFollowsTheDocumentedRulesIncludingNulls() throws Exception {
        // Independent computation: keys sorted, key=value joined by &, null as empty.
        TreeMap<String, Object> data = new TreeMap<>();
        data.put("orderCode", 10_000_123L);
        data.put("amount", 50_000);
        data.put("description", "VS123");
        data.put("counterAccountName", null);
        data.put("code", "00");
        data.put("paymentLinkId", "abc");
        data.put("reference", "REF1");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "00");
        payload.put("success", true);
        payload.put("data", new LinkedHashMap<>(data));
        payload.put("signature", sign(data));
        PaymentGateway.Callback cb = gateway.verify(payload);
        assertThat(cb.validSignature()).isTrue();
        assertThat(cb.merchantReference()).isEqualTo("10000123");
        assertThat(cb.amountVnd()).isEqualTo(50_000);
        assertThat(cb.providerTransactionNo()).isEqualTo("REF1");
        assertThat(cb.success()).isTrue();
        payload.put("signature", sign(data).replace('a', 'b'));
        assertThat(gateway.verify(payload).validSignature()).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private static PaymentGateway.SessionResult page(String url, String linkId) {
        return new PaymentGateway.SessionResult(PaymentGateway.Outcome.CREATED, url, linkId, "");
    }

    private ResultActions send(Map<String, Object> payload) throws Exception {
        return mvc.perform(
                post("/api/v1/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)));
    }

    private static Map<String, Object> webhook(long orderCode, long amount, String reference) {
        return webhook(orderCode, amount, reference, "00", true);
    }

    private static Map<String, Object> webhook(
            long orderCode, long amount, String reference, String code, boolean success) {
        TreeMap<String, Object> data = new TreeMap<>();
        data.put("orderCode", orderCode);
        data.put("amount", amount);
        data.put("description", "VS" + (orderCode % 10_000_000L));
        data.put("accountNumber", "12345678");
        data.put("reference", reference);
        data.put("transactionDateTime", "2026-11-20 10:30:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "link-x");
        data.put("code", code);
        data.put("desc", success ? "Thành công" : "Thất bại");
        data.put("counterAccountBankId", null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("desc", success ? "success" : "failed");
        out.put("success", success);
        out.put("data", new LinkedHashMap<>(data));
        out.put("signature", sign(data));
        return out;
    }

    private static String sign(TreeMap<String, Object> data) {
        StringBuilder text = new StringBuilder();
        data.forEach(
                (k, v) ->
                        text.append(text.isEmpty() ? "" : "&")
                                .append(k)
                                .append('=')
                                .append(v == null ? "" : String.valueOf(v)));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(mac.doFinal(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode placeOnline(String subject, int quantity, String key, int expected)
            throws Exception {
        UUID address = address(subject);
        mvc.perform(
                        post("/api/v1/cart/lines")
                                .with(buyer(subject))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"skuId\":\""
                                                + sku
                                                + "\",\"quantity\":"
                                                + quantity
                                                + "}"))
                .andExpect(status().isOk());
        JsonNode quote =
                body(
                        mvc.perform(
                                        post("/api/v1/checkout/quotes")
                                                .with(buyer(subject))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"addressId\":\"" + address + "\"}"))
                                .andExpect(status().isCreated()));
        return body(
                mvc.perform(
                                post("/api/v1/checkout/orders")
                                        .with(buyer(subject))
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"quoteId\":\""
                                                        + quote.get("id").asText()
                                                        + "\",\"paymentMethod\":\"PSP\"}"))
                        .andExpect(status().is(expected)));
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

    private String orderStatus() {
        return jdbc.sql("SELECT status FROM sales.seller_order").query(String.class).single();
    }

    private long onHand() {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(on_hand),0) FROM inventory.stock_balance WHERE sku_id=:s")
                .param("s", sku)
                .query(Long.class)
                .single();
    }

    private long reserved() {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(reserved),0) FROM inventory.stock_balance WHERE sku_id=:s")
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
}
