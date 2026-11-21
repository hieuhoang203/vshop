package vn.vshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
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
import vn.vshop.modules.order.service.RefundService;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.service.PolicyService;

/** Refunds of online payments: created with the cancel/reject, paid out by an administrator. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.payment.provider=vnpay",
            "vshop.payment.vnpay.tmn-code=TESTTMN1",
            "vshop.payment.vnpay.hash-secret=unit-test-secret-not-real",
            "vshop.payment.payos.client-id=",
            "vshop.payment.payos.api-key=",
            "vshop.payment.payos.checksum-key="
        })
@AutoConfigureMockMvc
@Transactional
class RefundIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRET = "unit-test-secret-not-real";
    private static final String SELLER = "refund-seller";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PolicyService policies;
    @Autowired RefundService refundService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean vn.vshop.modules.order.gateway.PayoutGateway payouts;

    private UUID shop;
    private UUID sku;

    @BeforeEach
    void seed() {
        UUID category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Refund Test',:slug)")
                .param("id", category)
                .param("slug", "refund-" + category)
                .update();
        shop = UUID.randomUUID();
        jdbc.sql(
                        "WITH a AS (INSERT INTO identity.account(external_subject) VALUES (:o) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,a.id,'Refund Shop','Refund test shop description','APPROVED' FROM a")
                .param("o", SELLER)
                .param("id", shop)
                .update();
        sku = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Refund product','Refund test product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "refund-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,100000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "REF-" + sku)
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
                                                5L))
                        .entrySet()) {
            policies.approve(
                    "refund-admin",
                    policies.createDraft("refund-admin", entry.getKey(), entry.getValue()).id());
        }
    }

    @Test
    void cancelingAPaidOrderCreatesOneRefundThatTheBuyerFillsAndAnAdminPaysOnce() throws Exception {
        String order = paidOrder("buyer-1", 2, "ref-key-0001"); // 2 x 100.000 + 25.000
        cancel("buyer-1", order).andExpect(status().isOk());

        // One refund for everything collected, waiting for the account.
        assertThat(count("payment.refund")).isEqualTo(1);
        mvc.perform(get("/api/v1/orders/" + order).with(buyer("buyer-1")))
                .andExpect(jsonPath("$.refund.status").value("AWAITING_ACCOUNT"))
                .andExpect(jsonPath("$.refund.amount").value(225_000))
                .andExpect(jsonPath("$.refund.reason").value("ORDER_CANCELED"))
                .andExpect(jsonPath("$.refund.needsAccount").value(true))
                .andExpect(
                        jsonPath("$.refund.accountLast4").value(org.hamcrest.Matchers.nullValue()));
        assertThat(
                        jdbc.sql(
                                        "SELECT sum(ra.amount) = (SELECT amount FROM payment.allocation) FROM payment.refund_allocation ra")
                                .query(Boolean.class)
                                .single())
                .isTrue();
        // The cause being reported again, or a goodwill refund of money already refunded, adds
        // nothing.
        refundService.createForOrder(UUID.fromString(order), "ORDER_CANCELED");
        refundService.createForOrder(UUID.fromString(order), "ADMIN_GOODWILL");
        assertThat(count("payment.refund")).isEqualTo(1);

        // Not payable before an account exists, and the account is validated.
        String refund =
                jdbc.sql("SELECT id::text FROM payment.refund").query(String.class).single();
        mvc.perform(
                        post("/api/v1/admin/refunds/" + refund + "/paid")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"payoutReference\":\"FT123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_STATE"));
        for (String bad :
                List.of(
                        "{\"bankName\":\"MB\",\"accountNumber\":\"12ab\",\"accountName\":\"Nguyen Van A\"}",
                        "{\"bankName\":\"MB\",\"accountNumber\":\"123456789\",\"accountName\":\"A1\"}",
                        "{\"bankName\":\"\",\"accountNumber\":\"123456789\",\"accountName\":\"Nguyen\"}")) {
            account("buyer-1", order, bad).andExpect(status().isBadRequest());
        }
        account("buyer-2", order, ACCOUNT).andExpect(status().isNotFound());
        account("buyer-1", order, ACCOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.accountLast4").value("6789"))
                .andExpect(jsonPath("$.needsAccount").value(false));
        assertThat(
                        jdbc.sql("SELECT account_number_ciphertext FROM payment.refund")
                                .query(String.class)
                                .single())
                .startsWith("v1.")
                .doesNotContain("123456789");

        // The administrator sees the whole account, sends the money and records the bank reference.
        mvc.perform(get("/api/v1/admin/refunds?status=APPROVED").with(buyer("buyer-1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/refunds?status=APPROVED").with(admin()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value("123456789"))
                .andExpect(jsonPath("$[0].accountName").value("NGUYEN VAN A"))
                .andExpect(jsonPath("$[0].amount").value(225_000));
        mvc.perform(
                        post("/api/v1/admin/refunds/" + refund + "/paid")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"payoutReference\":\"FT26324000111\",\"note\":\"MB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payoutReference").value("FT26324000111"));
        mvc.perform(
                        post("/api/v1/admin/refunds/" + refund + "/paid")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"payoutReference\":\"FT26324000112\"}"))
                .andExpect(status().isConflict());
        // Once paid the account can no longer be changed, and the buyer sees the outcome.
        account("buyer-1", order, ACCOUNT).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/" + order).with(buyer("buyer-1")))
                .andExpect(jsonPath("$.refund.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.refund.payoutReference").value("FT26324000111"));
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE action = 'REFUND_PAID' AND resource_id = :id::uuid")
                                .param("id", refund)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void aWrongAccountSendsTheRefundBackToTheBuyer() throws Exception {
        String order = paidOrder("buyer-1", 1, "ref-key-0002");
        cancel("buyer-1", order).andExpect(status().isOk());
        account("buyer-1", order, ACCOUNT).andExpect(status().isOk());
        String refund =
                jdbc.sql("SELECT id::text FROM payment.refund").query(String.class).single();
        mvc.perform(
                        post("/api/v1/admin/refunds/" + refund + "/bad-account")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Ngân hàng báo sai tên chủ tài khoản\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_ACCOUNT"))
                .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/orders/" + order).with(buyer("buyer-1")))
                .andExpect(jsonPath("$.refund.needsAccount").value(true));
        account("buyer-1", order, ACCOUNT).andExpect(status().isOk());
        assertThat(count("payment.refund")).isEqualTo(1);
    }

    @Test
    void aShopRejectingAPaidOrderRefundsItButACodOrderNeverDoes() throws Exception {
        String paid = paidOrder("buyer-1", 1, "ref-key-0003");
        mvc.perform(
                        post("/api/v1/seller/shops/" + shop + "/orders/" + paid + "/reject")
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Hết hàng thực tế ở kho\"}"))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.sql("SELECT reason || ':' || amount FROM payment.refund")
                                .query(String.class)
                                .single())
                .isEqualTo("SELLER_REJECTED:125000");

        String cod = codOrder("buyer-2");
        cancel("buyer-2", cod).andExpect(status().isOk());
        assertThat(count("payment.refund")).isEqualTo(1); // nothing was collected for the COD order
        account("buyer-2", cod, ACCOUNT).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/" + cod).with(buyer("buyer-2")))
                .andExpect(jsonPath("$.refund").value(org.hamcrest.Matchers.nullValue()));
    }

    // ------------------------------------------------------------------ helpers

    private static final String ACCOUNT =
            "{\"bankName\":\"MB Bank\",\"accountNumber\":\"123 456 789\",\"accountName\":\"Nguyễn Văn A\"}";

    // ------------------------------------------------------------------ automatic payout (R3)

    private static final String LISTED_BANK =
            "{\"bankCode\":\"VCB\",\"accountNumber\":\"0123 456 789\",\"accountName\":\"Nguyễn Văn A\"}";

    private String approvedRefund(String subject, String key, String accountBody) throws Exception {
        String order = paidOrder(subject, 1, key);
        cancel(subject, order).andExpect(status().isOk());
        account(subject, order, accountBody).andExpect(status().isOk());
        return jdbc.sql("SELECT id::text FROM payment.refund WHERE seller_order_id = :o::uuid")
                .param("o", order)
                .query(String.class)
                .single();
    }

    private ResultActions payout(String refund, String step) throws Exception {
        return mvc.perform(
                post("/api/v1/admin/refunds/" + refund + "/payout" + step).with(admin()));
    }

    private String refundStatus(String refund) {
        return jdbc.sql("SELECT status FROM payment.refund WHERE id = :id::uuid")
                .param("id", refund)
                .query(String.class)
                .single();
    }

    @Test
    void aPayoutIsSentOnceAndAnUnclearAnswerIsResolvedByAskingNotByResending() throws Exception {
        String refund = approvedRefund("buyer-1", "ref-key-0301", LISTED_BANK);
        org.mockito.Mockito.when(payouts.provider()).thenReturn("PAYOS");
        // Switched off: the hand-made transfer stays the only way.
        org.mockito.Mockito.when(payouts.enabled()).thenReturn(false);
        payout(refund, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYOUT_NOT_AVAILABLE"));
        org.mockito.Mockito.when(payouts.enabled()).thenReturn(true);
        mvc.perform(get("/api/v1/admin/refunds?status=APPROVED").with(admin()))
                .andExpect(jsonPath("$[0].canPayAutomatically").value(true));

        // The provider does not answer: the refund waits at SUBMITTED, nothing is paid.
        org.mockito.Mockito.when(payouts.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        new vn.vshop.modules.order.gateway.PayoutGateway.Result(
                                vn.vshop.modules.order.gateway.PayoutGateway.Outcome.UNKNOWN,
                                null,
                                "timeout"));
        payout(refund, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        org.mockito.ArgumentCaptor<vn.vshop.modules.order.gateway.PayoutGateway.Request> sent =
                org.mockito.ArgumentCaptor.forClass(
                        vn.vshop.modules.order.gateway.PayoutGateway.Request.class);
        org.mockito.Mockito.verify(payouts).submit(sent.capture());
        assertThat(sent.getValue().referenceId()).isEqualTo(refund);
        assertThat(sent.getValue().bankBin()).isEqualTo("970436");
        assertThat(sent.getValue().accountNumber()).isEqualTo("0123456789");
        assertThat(sent.getValue().amountVnd()).isEqualTo(125_000);

        // Pressing again asks the provider about the same reference; it never sends again.
        org.mockito.Mockito.when(payouts.query(refund))
                .thenReturn(
                        new vn.vshop.modules.order.gateway.PayoutGateway.Result(
                                vn.vshop.modules.order.gateway.PayoutGateway.Outcome.PROCESSING,
                                "P-1",
                                ""));
        payout(refund, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        org.mockito.Mockito.verify(payouts, org.mockito.Mockito.times(1))
                .submit(org.mockito.ArgumentMatchers.any());
        // Nobody can also record it by hand while the provider has it.
        mvc.perform(
                        post("/api/v1/admin/refunds/" + refund + "/paid")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"payoutReference\":\"HAND-1\"}"))
                .andExpect(status().isConflict());

        org.mockito.Mockito.when(payouts.query(refund))
                .thenReturn(
                        new vn.vshop.modules.order.gateway.PayoutGateway.Result(
                                vn.vshop.modules.order.gateway.PayoutGateway.Outcome.SUCCEEDED,
                                "TXN-9",
                                ""));
        payout(refund, "/check")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payoutReference").value("TXN-9"));
        assertThat(count("platform.outbox_event WHERE event_type = 'RefundSucceeded'"))
                .isEqualTo(1);
        // Once paid, neither button does anything more.
        payout(refund, "").andExpect(status().isConflict());
        org.mockito.Mockito.verify(payouts, org.mockito.Mockito.times(1))
                .submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aRefusedPayoutGoesBackToApprovedAndCanStillBePaidByHand() throws Exception {
        String refund = approvedRefund("buyer-1", "ref-key-0302", LISTED_BANK);
        org.mockito.Mockito.when(payouts.provider()).thenReturn("PAYOS");
        org.mockito.Mockito.when(payouts.enabled()).thenReturn(true);
        org.mockito.Mockito.when(payouts.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        new vn.vshop.modules.order.gateway.PayoutGateway.Result(
                                vn.vshop.modules.order.gateway.PayoutGateway.Outcome.FAILED,
                                null,
                                "Sai số tài khoản"));
        payout(refund, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.payoutFailure").value("Sai số tài khoản"))
                .andExpect(jsonPath("$.canPayAutomatically").value(true));
        mvc.perform(
                        post("/api/v1/admin/refunds/" + refund + "/paid")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"payoutReference\":\"HAND-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void anAccountTypedByHandHasNoBankNumberSoItCannotBePaidAutomatically() throws Exception {
        String refund =
                approvedRefund(
                        "buyer-1",
                        "ref-key-0303",
                        "{\"bankName\":\"Ngân hàng X\",\"accountNumber\":\"123456789\",\"accountName\":\"Nguyen Van A\"}");
        org.mockito.Mockito.when(payouts.provider()).thenReturn("PAYOS");
        org.mockito.Mockito.when(payouts.enabled()).thenReturn(true);
        payout(refund, "").andExpect(status().isConflict());
        org.mockito.Mockito.verify(payouts, org.mockito.Mockito.never())
                .submit(org.mockito.ArgumentMatchers.any());
        assertThat(refundStatus(refund)).isEqualTo("APPROVED");
        // An unlisted bank code is refused for the buyer.
        String order =
                jdbc.sql("SELECT seller_order_id::text FROM payment.refund WHERE id = :id::uuid")
                        .param("id", refund)
                        .query(String.class)
                        .single();
        account(
                        "buyer-1",
                        order,
                        "{\"bankCode\":\"XXX\",\"accountNumber\":\"123456789\",\"accountName\":\"Nguyen\"}")
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/banks").with(buyer("buyer-1")))
                .andExpect(jsonPath("$[0].code").value("VCB"));
    }

    private ResultActions cancel(String subject, String order) throws Exception {
        return mvc.perform(
                post("/api/v1/orders/" + order + "/cancel")
                        .with(buyer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Tôi đổi ý không mua nữa\"}"));
    }

    private ResultActions account(String subject, String order, String body) throws Exception {
        return mvc.perform(
                put("/api/v1/orders/" + order + "/refund-account")
                        .with(buyer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    /** An online order paid through a correctly signed VNPay callback, now waiting for the shop. */
    private String paidOrder(String subject, int quantity, String key) throws Exception {
        JsonNode group = placeOrder(subject, quantity, key, "PSP");
        String ref = query(group.get("paymentUrl").asText()).get("vnp_TxnRef");
        long amount = group.get("totalAmount").asLong() * 100;
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Amount", Long.toString(amount));
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_OrderInfo", "Thanh toan don hang VShop");
        p.put("vnp_PayDate", "20261120103000");
        p.put("vnp_ResponseCode", "00");
        p.put("vnp_TmnCode", "TESTTMN1");
        p.put("vnp_TransactionNo", "TX" + key);
        p.put("vnp_TransactionStatus", "00");
        p.put("vnp_TxnRef", ref);
        var request = get("/api/v1/payments/vnpay/ipn");
        p.forEach(request::param);
        request.param("vnp_SecureHash", sign(p));
        mvc.perform(request).andExpect(jsonPath("$.RspCode").value("00"));
        return group.get("orders").get(0).get("id").asText();
    }

    private String codOrder(String subject) throws Exception {
        return placeOrder(subject, 1, "cod-" + UUID.randomUUID(), "COD")
                .get("orders")
                .get(0)
                .get("id")
                .asText();
    }

    private JsonNode placeOrder(String subject, int quantity, String key, String method)
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
                                                        + "\",\"paymentMethod\":\""
                                                        + method
                                                        + "\"}"))
                        .andExpect(status().isCreated()));
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

    private static String sign(Map<String, String> params) {
        StringBuilder data = new StringBuilder();
        new TreeMap<>(params)
                .forEach(
                        (k, v) -> {
                            if (v != null && !v.isEmpty()) {
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

    private static JwtRequestPostProcessor seller() {
        return jwt().jwt(j -> j.subject(SELLER))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(
                        j -> {
                            j.subject("refund-admin");
                            j.claim("amr", List.of("pwd", "otp"));
                        })
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
