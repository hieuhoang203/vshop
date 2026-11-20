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
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.service.PolicyService;

/** Return cases after delivery: shop and administrator decisions create refunds in the ledger. */
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
class ReturnCaseIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRET = "unit-test-secret-not-real";
    private static final String SELLER = "return-seller";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PolicyService policies;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID shop;
    private UUID sku;

    @BeforeEach
    void seed() {
        UUID category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Return Test',:slug)")
                .param("id", category)
                .param("slug", "return-" + category)
                .update();
        shop = UUID.randomUUID();
        jdbc.sql(
                        "WITH a AS (INSERT INTO identity.account(external_subject) VALUES (:o) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,a.id,'Return Shop','Return test shop description','APPROVED' FROM a")
                .param("o", SELLER)
                .param("id", shop)
                .update();
        sku = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Return product','Return test product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "return-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,100000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "RET-" + sku)
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
                    "return-admin",
                    policies.createDraft("return-admin", entry.getKey(), entry.getValue()).id());
        }
    }

    private void approveReturnHandling() {
        policies.approve(
                "return-admin",
                policies.createDraft(
                                "return-admin",
                                PolicyType.RETURN_HANDLING,
                                Map.of("sellerResponseHours", 48L))
                        .id());
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    void aCaseNeedsADeliveredOrderInsideTheWindowAndAnApprovedHandlingPolicy() throws Exception {
        String order = deliveredCod("buyer-1", 2, "ret-key-0001");
        // No handling policy yet.
        openCase("buyer-1", order, "DAMAGED", "REFUND_ONLY", "Hộp bị móp, sản phẩm nứt")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_POLICY_MISSING"));
        approveReturnHandling();
        // Validation at the edge and in the service.
        openCase("buyer-1", order, "NOPE", "REFUND_ONLY", "Hộp bị móp, sản phẩm nứt")
                .andExpect(status().isBadRequest());
        openCase("buyer-1", order, "DAMAGED", "REFUND_ONLY", "ngắn")
                .andExpect(status().isBadRequest());
        openCase("buyer-1", order, "CHANGED_MIND", "REFUND_ONLY", "Tôi đổi ý không muốn giữ nữa")
                .andExpect(status().isBadRequest());
        openCase("buyer-2", order, "DAMAGED", "REFUND_ONLY", "Hộp bị móp, sản phẩm nứt")
                .andExpect(status().isNotFound());
        // Past the return window.
        jdbc.sql("UPDATE sales.seller_order SET return_eligible_until = now() - interval '1 hour'")
                .update();
        openCase("buyer-1", order, "DAMAGED", "REFUND_ONLY", "Hộp bị móp, sản phẩm nứt")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_NOT_ALLOWED"));
        jdbc.sql("UPDATE sales.seller_order SET return_eligible_until = now() + interval '3 days'")
                .update();
        // Not delivered.
        String early = placeCod("buyer-1", 1, "ret-key-0002");
        openCase("buyer-1", early, "DAMAGED", "REFUND_ONLY", "Hộp bị móp, sản phẩm nứt")
                .andExpect(status().isConflict());
        // One case per order.
        openCase("buyer-1", order, "DAMAGED", "REFUND_ONLY", "Hộp bị móp, sản phẩm nứt")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requestedAmount").value(225_000));
        openCase("buyer-1", order, "DEFECTIVE", "REFUND_ONLY", "Không hoạt động được nữa")
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ shop offers money back

    @Test
    void aPartialOfferAcceptedRefundsThatAmountOnlineAndTheCapHolds() throws Exception {
        approveReturnHandling();
        String order = deliveredPaid("buyer-1", 2, "ret-key-0003"); // paid online 225.000
        String id =
                caseId(
                        openCase(
                                "buyer-1",
                                order,
                                "DAMAGED",
                                "REFUND_ONLY",
                                "Hộp bị móp, sản phẩm nứt"));
        // Offers above the request or from another shop are refused.
        offer(id, 300_000).andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/seller/shops/"
                                        + shop
                                        + "/return-cases/"
                                        + id
                                        + "/offer-refund")
                                .with(seller("someone-else"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":100000}"))
                .andExpect(status().isNotFound());
        offer(id, 150_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELLER_OFFERED"))
                .andExpect(jsonPath("$.offeredAmount").value(150_000));
        // Only the buyer decides; steps cannot be repeated.
        mvc.perform(post("/api/v1/return-cases/" + id + "/accept-offer").with(buyer("buyer-2")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/return-cases/" + id + "/accept-offer").with(buyer("buyer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(150_000));
        mvc.perform(post("/api/v1/return-cases/" + id + "/accept-offer").with(buyer("buyer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RETURN_STATE"));

        assertThat(
                        jdbc.sql(
                                        "SELECT source || ':' || reason || ':' || amount || ':' || (return_case_id IS NOT NULL) FROM payment.refund")
                                .query(String.class)
                                .single())
                .isEqualTo("ONLINE:RETURN_APPROVED:150000:true");
        assertThat(
                        jdbc.sql("SELECT sum(amount) FROM payment.refund_allocation")
                                .query(Long.class)
                                .single())
                .isEqualTo(150_000);
        // The buyer keeps the goods: stock is unchanged (20 - 2).
        assertThat(onHand()).isEqualTo(18);
        mvc.perform(get("/api/v1/orders/" + order).with(buyer("buyer-1")))
                .andExpect(jsonPath("$.refund.amount").value(150_000))
                .andExpect(jsonPath("$.refund.needsAccount").value(true));
    }

    // ------------------------------------------------------------------ return of the goods (cash
    // on delivery)

    @Test
    void returningTheGoodsRefundsTheItemsAndPutsStockBackForACodOrder() throws Exception {
        approveReturnHandling();
        String order =
                deliveredCod("buyer-1", 2, "ret-key-0004"); // 200.000 items + 25.000 shipping
        assertThat(onHand()).isEqualTo(18);
        String id =
                caseId(
                        openCase(
                                "buyer-1",
                                order,
                                "CHANGED_MIND",
                                "RETURN_AND_REFUND",
                                "Tôi đổi ý không muốn giữ nữa"));
        // Changing one's mind does not refund the shipping fee.
        mvc.perform(get("/api/v1/orders/" + order + "/return-case").with(buyer("buyer-1")))
                .andExpect(jsonPath("$.requestedAmount").value(200_000));
        // A refund-only offer is not allowed for a change of mind.
        offer(id, 100_000).andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/seller/shops/"
                                        + shop
                                        + "/return-cases/"
                                        + id
                                        + "/approve-return")
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Gửi lại về kho giúp shop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_RETURN"))
                .andExpect(jsonPath("$.resolution").value("RETURN_AND_REFUND"));
        // The money waits for the goods: confirming before shipping is refused.
        confirmReceived(id, true).andExpect(status().isConflict());
        mvc.perform(
                        post("/api/v1/return-cases/" + id + "/ship")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"trackingCode\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/return-cases/" + id + "/ship")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"trackingCode\":\"RET-VN-0001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_RETURN"))
                .andExpect(jsonPath("$.returnTrackingCode").value("RET-VN-0001"));
        confirmReceived(id, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(200_000));

        assertThat(
                        jdbc.sql(
                                        "SELECT source || ':' || amount || ':' || (charge_id IS NULL) FROM payment.refund")
                                .query(String.class)
                                .single())
                .isEqualTo("COD:200000:true");
        assertThat(onHand()).isEqualTo(20); // the returned goods are sellable again, once
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM aftersales.return_case_event WHERE return_case_id = :id::uuid")
                                .param("id", id)
                                .query(Long.class)
                                .single())
                .isEqualTo(4);
        // The R0 payout path works for a cash order too.
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/api/v1/orders/" + order + "/refund-account")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"bankName\":\"MB\",\"accountNumber\":\"123456789\",\"accountName\":\"Nguyen Van A\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ administrator

    @Test
    void anAdministratorSettlesDeclinedRejectedAndUnansweredCases() throws Exception {
        approveReturnHandling();
        // Declined offer, then the administrator refunds less than requested.
        String a = deliveredPaid("buyer-1", 1, "ret-key-0005");
        String caseA =
                caseId(
                        openCase(
                                "buyer-1",
                                a,
                                "NOT_AS_DESCRIBED",
                                "REFUND_ONLY",
                                "Màu khác hẳn mô tả của shop"));
        offer(caseA, 50_000).andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/return-cases/" + caseA + "/decline-offer")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Ít quá\"}"))
                .andExpect(jsonPath("$.status").value("REVIEWING"));
        mvc.perform(get("/api/v1/admin/return-cases").with(buyer("buyer-1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/return-cases").with(admin()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(caseA));
        decide(caseA, "REFUND_ONLY", 90_000L, "Hoàn một phần theo bằng chứng")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(90_000));
        decide(caseA, "REJECT", null, "Không thể quyết định hai lần")
                .andExpect(status().isConflict());

        // Shop rejects, buyer escalates, the administrator rejects too.
        String b = deliveredCod("buyer-2", 1, "ret-key-0006");
        String caseB =
                caseId(
                        openCase(
                                "buyer-2",
                                b,
                                "FAKE",
                                "REFUND_ONLY",
                                "Tôi nghi ngờ đây là hàng giả"));
        mvc.perform(
                        post("/api/v1/seller/shops/" + shop + "/return-cases/" + caseB + "/reject")
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Hàng chính hãng, có hóa đơn\"}"))
                .andExpect(jsonPath("$.status").value("SELLER_REJECTED"));
        mvc.perform(
                        post("/api/v1/return-cases/" + caseB + "/escalate")
                                .with(buyer("buyer-2"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(jsonPath("$.status").value("REVIEWING"));
        decide(caseB, "REJECT", null, "Không đủ bằng chứng hàng giả")
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(count("payment.refund"))
                .isEqualTo(1); // nothing was refunded for the rejected case

        // A shop that never answers: the case reaches the administrator after the deadline.
        String c = deliveredCod("buyer-3", 1, "ret-key-0007");
        String caseC =
                caseId(
                        openCase(
                                "buyer-3",
                                c,
                                "MISSING_ITEMS",
                                "REFUND_ONLY",
                                "Thiếu một sản phẩm trong gói"));
        decide(caseC, "REFUND_ONLY", null, "Quá hạn phản hồi").andExpect(status().isConflict());
        jdbc.sql(
                        "UPDATE aftersales.return_case SET seller_deadline_at = now() - interval '1 hour' WHERE id = :id::uuid")
                .param("id", caseC)
                .update();
        mvc.perform(get("/api/v1/admin/return-cases").with(admin()))
                .andExpect(jsonPath("$[?(@.id=='" + caseC + "')].status").value("REQUESTED"));
        decide(caseC, "RETURN_AND_REFUND", null, "Yêu cầu trả hàng để kiểm")
                .andExpect(jsonPath("$.status").value("AWAITING_RETURN"));
        // Buyer cancels instead of returning; a new case cannot replace a decided one but this one
        // is free.
        mvc.perform(post("/api/v1/return-cases/" + caseC + "/cancel").with(buyer("buyer-3")))
                .andExpect(jsonPath("$.status").value("CANCELED"));
        openCase("buyer-3", c, "MISSING_ITEMS", "REFUND_ONLY", "Thiếu một sản phẩm trong gói")
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions openCase(
            String subject, String order, String reason, String resolution, String text)
            throws Exception {
        return mvc.perform(
                post("/api/v1/orders/" + order + "/return-case")
                        .with(buyer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"reasonCode\":\""
                                        + reason
                                        + "\",\"preferredResolution\":\""
                                        + resolution
                                        + "\",\"description\":\""
                                        + text
                                        + "\"}"));
    }

    private ResultActions offer(String id, long amount) throws Exception {
        return mvc.perform(
                post("/api/v1/seller/shops/" + shop + "/return-cases/" + id + "/offer-refund")
                        .with(seller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"));
    }

    private ResultActions confirmReceived(String id, boolean restock) throws Exception {
        return mvc.perform(
                post("/api/v1/seller/shops/" + shop + "/return-cases/" + id + "/confirm-received")
                        .with(seller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restock\":" + restock + "}"));
    }

    private ResultActions decide(String id, String decision, Long amount, String note)
            throws Exception {
        return mvc.perform(
                post("/api/v1/admin/return-cases/" + id + "/decide")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"decision\":\""
                                        + decision
                                        + "\""
                                        + (amount == null ? "" : ",\"amount\":" + amount)
                                        + ",\"note\":\""
                                        + note
                                        + "\"}"));
    }

    private static String caseId(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    /** A COD order taken through confirm, pack, ready, ship and delivered. */
    private String deliveredCod(String subject, int quantity, String key) throws Exception {
        return deliver(placeCod(subject, quantity, key));
    }

    private String deliveredPaid(String subject, int quantity, String key) throws Exception {
        return deliver(paidOnline(subject, quantity, key));
    }

    private String deliver(String order) throws Exception {
        String base = "/api/v1/seller/shops/" + shop + "/orders/" + order;
        for (String step : new String[] {"confirm", "pack", "ready"}) {
            mvc.perform(post(base + "/" + step).with(seller())).andExpect(status().isOk());
        }
        mvc.perform(
                        post(base + "/ship")
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"trackingCode\":\"SHIP-"
                                                + order.substring(0, 8)
                                                + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/delivered").with(seller())).andExpect(status().isOk());
        return order;
    }

    private String placeCod(String subject, int quantity, String key) throws Exception {
        return placeOrder(subject, quantity, key, "COD").get("orders").get(0).get("id").asText();
    }

    private String paidOnline(String subject, int quantity, String key) throws Exception {
        JsonNode group = placeOrder(subject, quantity, key, "PSP");
        String ref = query(group.get("paymentUrl").asText()).get("vnp_TxnRef");
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Amount", Long.toString(group.get("totalAmount").asLong() * 100));
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

    private long onHand() {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(on_hand),0) FROM inventory.stock_balance WHERE sku_id=:s")
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

    private static JwtRequestPostProcessor seller() {
        return seller(SELLER);
    }

    private static JwtRequestPostProcessor seller(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(
                        j -> {
                            j.subject("return-admin");
                            j.claim("amr", List.of("pwd", "otp"));
                        })
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
