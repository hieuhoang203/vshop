package vn.vshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
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
import vn.vshop.modules.order.service.PaymentService;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.service.PolicyService;

/** Online payment with VNPay: signed callbacks drive the order; the browser proves nothing. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.payment.vnpay.tmn-code=TESTTMN1",
            "vshop.payment.vnpay.hash-secret=unit-test-secret-not-real",
            "vshop.payment.return-base-url=https://shop.example.test"
        })
@AutoConfigureMockMvc
@Transactional
class PaymentIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRET = "unit-test-secret-not-real";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PolicyService policies;
    @Autowired PaymentService payments;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID sku;
    private String lastQuoteId;

    @BeforeEach
    void seed() {
        UUID category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Pay Test',:slug)")
                .param("id", category)
                .param("slug", "pay-" + category)
                .update();
        UUID shop = UUID.randomUUID();
        jdbc.sql(
                        "WITH a AS (INSERT INTO identity.account(external_subject) VALUES ('pay-seller') ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,a.id,'Pay Shop','Payment test shop description','APPROVED' FROM a")
                .param("id", shop)
                .update();
        sku = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Pay product','Payment test product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "pay-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,100000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "PAY-" + sku)
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
                    "pay-admin",
                    policies.createDraft("pay-admin", entry.getKey(), entry.getValue()).id());
        }
    }

    @Test
    void anOnlineOrderWaitsForPaymentHoldsStockAndGetsAStableSignedUrl() throws Exception {
        JsonNode group = placeOnline("buyer-1", 2, "psp-key-0001", 201);
        assertThat(group.get("paymentMethod").asText()).isEqualTo("PSP");
        assertThat(group.get("totalAmount").asLong()).isEqualTo(225_000); // 2 x 100.000 + 25.000
        JsonNode order = group.get("orders").get(0);
        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("paymentDeadline").isNull()).isFalse();
        // Stock is held, not consumed.
        assertThat(onHand()).isEqualTo(10);
        assertThat(reserved()).isEqualTo(2);

        Map<String, String> query = query(group.get("paymentUrl").asText());
        assertThat(group.get("paymentUrl").asText())
                .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(query.get("vnp_Amount")).isEqualTo("22500000");
        assertThat(query.get("vnp_TmnCode")).isEqualTo("TESTTMN1");
        assertThat(query.get("vnp_ReturnUrl"))
                .isEqualTo("https://shop.example.test/checkout/result/" + group.get("id").asText());
        // The signature follows the documented algorithm, computed here independently.
        assertThat(query.get("vnp_SecureHash")).isEqualTo(sign(query));

        // A retry of the same request gets the same payment URL and no second order.
        JsonNode again = replayOnline("buyer-1", "psp-key-0001", 200);
        assertThat(again.get("paymentUrl").asText()).isEqualTo(group.get("paymentUrl").asText());
        assertThat(count("sales.seller_order")).isEqualTo(1);
        assertThat(count("payment.attempt")).isEqualTo(1);
    }

    @Test
    void aSignedSuccessCallbackConfirmsTheOrderOnceAndMovesStock() throws Exception {
        JsonNode group = placeOnline("buyer-1", 2, "psp-key-0002", 201);
        String ref = query(group.get("paymentUrl").asText()).get("vnp_TxnRef");

        ipn(ipnParams(ref, 22_500_000, "00", "00", "14000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(orderStatus()).isEqualTo("AWAITING_SELLER");
        assertThat(onHand()).isEqualTo(8);
        assertThat(reserved()).isZero();
        assertThat(
                        jdbc.sql("SELECT sum(amount) FROM payment.allocation")
                                .query(Long.class)
                                .single())
                .isEqualTo(225_000);
        assertThat(jdbc.sql("SELECT status FROM payment.intent").query(String.class).single())
                .isEqualTo("PAID");
        mvc.perform(
                        get("/api/v1/checkout/groups/" + group.get("id").asText())
                                .with(buyer("buyer-1")))
                .andExpect(jsonPath("$.orders[0].status").value("AWAITING_SELLER"))
                .andExpect(jsonPath("$.paymentUrl").value(org.hamcrest.Matchers.nullValue()));

        // The provider repeats the notification: acknowledged, nothing counted twice.
        ipn(ipnParams(ref, 22_500_000, "00", "00", "14000001"))
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(count("payment.charge")).isEqualTo(1);
        assertThat(onHand()).isEqualTo(8);
    }

    @Test
    void forgedWrongAmountUnknownAndFailedCallbacksNeverConfirmAnOrder() throws Exception {
        JsonNode group = placeOnline("buyer-1", 1, "psp-key-0003", 201);
        String ref = query(group.get("paymentUrl").asText()).get("vnp_TxnRef");

        Map<String, String> forged = ipnParams(ref, 12_500_000, "00", "00", "14000002");
        forged.put("vnp_SecureHash", "0".repeat(128));
        ipn(forged).andExpect(jsonPath("$.RspCode").value("97"));
        Map<String, String> tampered = ipnParams(ref, 12_500_000, "00", "00", "14000002");
        tampered.put("vnp_Amount", "100"); // changed after signing
        ipn(tampered).andExpect(jsonPath("$.RspCode").value("97"));
        ipn(ipnParams("NOSUCHREF", 12_500_000, "00", "00", "14000003"))
                .andExpect(jsonPath("$.RspCode").value("01"));
        // Correctly signed but for less money than owed.
        ipn(ipnParams(ref, 100_000, "00", "00", "14000004"))
                .andExpect(jsonPath("$.RspCode").value("04"));
        assertThat(orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(count("payment.charge")).isZero();

        // The customer cancelled at the bank: acknowledged, order still waiting, attempt failed.
        ipn(ipnParams(ref, 12_500_000, "24", "02", "0"))
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(count("payment.charge")).isZero();
        assertThat(jdbc.sql("SELECT status FROM payment.attempt").query(String.class).single())
                .isEqualTo("FAILED");
        // The browser landing on the result page changes nothing either.
        mvc.perform(
                        get("/api/v1/checkout/groups/" + group.get("id").asText())
                                .with(buyer("buyer-1")))
                .andExpect(jsonPath("$.orders[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void unpaidOrdersExpireAndALatePaymentGoesToReviewWithoutRevivingTheOrder() throws Exception {
        JsonNode group = placeOnline("buyer-1", 2, "psp-key-0004", 201);
        String ref = query(group.get("paymentUrl").asText()).get("vnp_TxnRef");
        assertThat(reserved()).isEqualTo(2);

        // Before the deadline nothing is canceled.
        assertThat(payments.expireUnpaid(10)).isZero();
        jdbc.sql("UPDATE payment.intent SET payment_deadline = now() - interval '1 minute'")
                .update();
        jdbc.sql("UPDATE sales.seller_order SET payment_deadline = now() - interval '1 minute'")
                .update();
        assertThat(payments.expireUnpaid(10)).isEqualTo(1);
        assertThat(payments.expireUnpaid(10)).isZero(); // repeating is a no-op
        assertThat(orderStatus()).isEqualTo("CANCELED");
        assertThat(reserved()).isZero();
        assertThat(onHand()).isEqualTo(10);

        // The provider reports a successful payment after all: recorded, order not revived.
        ipn(ipnParams(ref, 22_500_000, "00", "00", "14000009"))
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(orderStatus()).isEqualTo("CANCELED");
        assertThat(onHand()).isEqualTo(10);
        assertThat(count("payment.charge")).isEqualTo(1);
        assertThat(count("payment.allocation")).isZero();
        assertThat(jdbc.sql("SELECT reason FROM payment.review_case").query(String.class).single())
                .isEqualTo("PAID_AFTER_EXPIRY");
        assertThat(jdbc.sql("SELECT status FROM payment.intent").query(String.class).single())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void theCallbackIsPublicButOnlyTheSignatureOpensIt() throws Exception {
        mvc.perform(get("/api/v1/payments/vnpay/ipn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
    }

    // ------------------------------------------------------------------ helpers

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
        lastQuoteId = quote.get("id").asText();
        return replayOnline(subject, key, expected);
    }

    /** Sends the order request for the last quote again, as a client retrying after a timeout. */
    private JsonNode replayOnline(String subject, String key, int expected) throws Exception {
        return body(
                mvc.perform(
                                post("/api/v1/checkout/orders")
                                        .with(buyer(subject))
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"quoteId\":\""
                                                        + lastQuoteId
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
                                                        """
                                                        {"recipientName":"Nguyen Van A","phone":"0912345678",
                                                         "street":"12 Nguyen Hue","ward":"Ben Nghe",
                                                         "province":"Ho Chi Minh"}
                                                        """))
                                .andExpect(status().isCreated()));
        return UUID.fromString(created.get("id").asText());
    }

    private ResultActions ipn(Map<String, String> params) throws Exception {
        var request = get("/api/v1/payments/vnpay/ipn");
        params.forEach(request::param);
        return mvc.perform(request);
    }

    /** A callback as VNPay would send it, signed with the shared secret. */
    private static Map<String, String> ipnParams(
            String ref, long amountVndTimes100, String code, String txStatus, String txNo) {
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Amount", Long.toString(amountVndTimes100));
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_OrderInfo", "Thanh toan don hang VShop");
        p.put("vnp_PayDate", "20261120103000");
        p.put("vnp_ResponseCode", code);
        p.put("vnp_TmnCode", "TESTTMN1");
        p.put("vnp_TransactionNo", txNo);
        p.put("vnp_TransactionStatus", txStatus);
        p.put("vnp_TxnRef", ref);
        Map<String, String> out = new LinkedHashMap<>(p);
        out.put("vnp_SecureHash", sign(p));
        return out;
    }

    /** HMAC-SHA512 over the sorted, URL-encoded parameters, excluding the hash itself. */
    private static String sign(Map<String, String> params) {
        StringBuilder data = new StringBuilder();
        new TreeMap<>(params)
                .forEach(
                        (k, v) -> {
                            if (!k.equals("vnp_SecureHash") && v != null && !v.isEmpty()) {
                                data.append(data.isEmpty() ? "" : "&")
                                        .append(k)
                                        .append('=')
                                        .append(URLEncoder.encode(v, StandardCharsets.US_ASCII));
                            }
                        });
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of()
                    .formatHex(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> query(String url) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            out.put(
                    URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
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
