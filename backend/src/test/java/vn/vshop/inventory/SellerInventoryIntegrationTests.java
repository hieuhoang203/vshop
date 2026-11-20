package vn.vshop.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.inventory.service.ReservationService;

/**
 * HTTP-level seller stock commands. Runs inside a rolled-back transaction, so failure paths that
 * need a real rollback are covered in {@link ReservationConcurrencyTests}.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false"
        })
@AutoConfigureMockMvc
@Transactional
class SellerInventoryIntegrationTests {
    private static final String OWNER = "inventory-owner";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ReservationService reservations;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID shop;
    private UUID sku;

    @BeforeEach
    void seed() {
        shop = createShop(OWNER, "APPROVED");
        sku = createSku(shop, "INV-1");
    }

    @Test
    void receiptsAndAdjustmentsChangeOnHandWithMovementsAndAudit() throws Exception {
        adjust(OWNER, shop, sku, "RECEIPT", 10, "Nhập lô đầu tiên", "key-receipt-1", 200)
                .andExpect(jsonPath("$.onHand").value(10))
                .andExpect(jsonPath("$.available").value(10));
        adjust(OWNER, shop, sku, "ADJUSTMENT", -3, "Kiểm kê hụt 3 cái", "key-adjust-1", 200)
                .andExpect(jsonPath("$.onHand").value(7))
                .andExpect(jsonPath("$.available").value(7));

        mvc.perform(inventory(shop, "/skus/" + sku + "/movements").with(actor(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(inventory(shop, "/skus/" + sku).with(actor(OWNER)))
                .andExpect(jsonPath("$[0].onHand").value(7));
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE actor_subject=:a AND resource_id=:s AND action='StockAdjusted'")
                                .param("a", OWNER)
                                .param("s", shop)
                                .query(Long.class)
                                .single())
                .isEqualTo(2);
        // The seller catalog view reads the same authoritative balance.
        mvc.perform(get("/api/v1/seller/shops/" + shop + "/products").with(actor(OWNER)))
                .andExpect(jsonPath("$.items[0].skus[0].available").value(7));
    }

    @Test
    void invalidStockInputIsRejectedBeforeAnyChange() throws Exception {
        adjust(OWNER, shop, sku, "RECEIPT", -1, "Âm khi nhập kho", "key-bad-1", 400);
        adjust(OWNER, shop, sku, "ADJUSTMENT", 0, "Không thay đổi gì", "key-bad-2", 400);
        adjust(
                OWNER,
                shop,
                sku,
                "ADJUSTMENT",
                1_000_001,
                "Quá lớn so với giới hạn",
                "key-bad-3",
                400);
        adjust(OWNER, shop, sku, "RECEIPT", 1, "ngắn", "key-bad-4", 400);
        adjust(OWNER, shop, sku, "RETURN", 1, "Loại không cho seller dùng", "key-bad-5", 400);
        adjust(OWNER, shop, sku, "RECEIPT", 1, "Khóa quá ngắn", "k", 400);
        mvc.perform(
                        post("/api/v1/seller/shops/" + shop + "/inventory/adjustments")
                                .with(actor(OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(sku, "RECEIPT", 1, "Thiếu header khóa")))
                .andExpect(status().isBadRequest());
        assertThat(count("inventory.stock_movement")).isZero();
    }

    @Test
    void sameKeyAppliesOnceAndDifferentPayloadIsRejected() throws Exception {
        for (int i = 0; i < 3; i++) {
            adjust(OWNER, shop, sku, "RECEIPT", 5, "Nhập hàng lặp lại", "key-replay-1", 200)
                    .andExpect(jsonPath("$.onHand").value(5));
        }
        assertThat(count("inventory.stock_movement")).isEqualTo(1);
        adjust(OWNER, shop, sku, "RECEIPT", 6, "Cùng khóa khác số", "key-replay-1", 409)
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(
                        jdbc.sql("SELECT on_hand FROM inventory.stock_balance WHERE sku_id=:s")
                                .param("s", sku)
                                .query(Long.class)
                                .single())
                .isEqualTo(5);
    }

    @Test
    void cannotReduceStockBelowHeldQuantity() throws Exception {
        adjust(OWNER, shop, sku, "RECEIPT", 10, "Nhập kho ban đầu", "key-held-0", 200);
        reservations.reserve(sku, 4, UUID.randomUUID(), "held-key-1", Duration.ofMinutes(15));
        adjust(OWNER, shop, sku, "ADJUSTMENT", -7, "Giảm sâu hơn lượng giữ", "key-held-1", 409)
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        adjust(OWNER, shop, sku, "ADJUSTMENT", -6, "Giảm đúng bằng phần rảnh", "key-held-2", 200)
                .andExpect(jsonPath("$.onHand").value(4))
                .andExpect(jsonPath("$.reserved").value(4))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void stockRoutesEnforceShopOwnershipAndApproval() throws Exception {
        createShop("inventory-other", "APPROVED");
        adjust(
                "inventory-other",
                shop,
                sku,
                "RECEIPT",
                1,
                "Seller khác thử nhập",
                "key-iso-1",
                404);
        mvc.perform(inventory(shop, "/skus/" + sku).with(actor("inventory-other")))
                .andExpect(status().isNotFound());
        mvc.perform(inventory(shop, "/skus/" + sku + "/movements").with(actor("inventory-other")))
                .andExpect(status().isNotFound());
        mvc.perform(inventory(shop, "/skus/" + sku)).andExpect(status().isUnauthorized());

        // A SKU of another shop is unreachable through the caller's own shop path.
        UUID otherShop = createShop("inventory-second", "APPROVED");
        UUID foreignSku = createSku(otherShop, "FOREIGN-1");
        adjust(OWNER, shop, foreignSku, "RECEIPT", 1, "SKU của shop khác", "key-iso-2", 404);
        assertThat(count("inventory.stock_movement")).isZero();

        UUID pending = createShop("inventory-pending", "SUBMITTED");
        adjust(
                        "inventory-pending",
                        pending,
                        createSku(pending, "PEND-1"),
                        "RECEIPT",
                        1,
                        "Shop chưa được duyệt",
                        "key-iso-3",
                        409)
                .andExpect(jsonPath("$.code").value("INVALID_SHOP_STATE"));
    }

    private org.springframework.test.web.servlet.ResultActions adjust(
            String subject,
            UUID shopId,
            UUID skuId,
            String type,
            long delta,
            String reason,
            String key,
            int expected)
            throws Exception {
        return mvc.perform(
                        post("/api/v1/seller/shops/" + shopId + "/inventory/adjustments")
                                .with(actor(subject))
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(skuId, type, delta, reason)))
                .andExpect(status().is(expected));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            inventory(UUID shopId, String path) {
        return get("/api/v1/seller/shops/" + shopId + "/inventory" + path);
    }

    private static String body(UUID skuId, String type, long delta, String reason) {
        return "{\"skuId\":\""
                + skuId
                + "\",\"type\":\""
                + type
                + "\",\"delta\":"
                + delta
                + ",\"reason\":\""
                + reason
                + "\"}";
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static JwtRequestPostProcessor actor(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    private UUID createShop(String subject, String shopStatus) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "WITH account AS (INSERT INTO identity.account(external_subject) VALUES (:owner) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,account.id,'Inventory Shop','Inventory test shop description only',:status FROM account")
                .param("id", id)
                .param("owner", subject)
                .param("status", shopStatus)
                .update();
        return id;
    }

    private UUID createSku(UUID shopId, String code) {
        UUID category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Inv Test',:slug)")
                .param("id", category)
                .param("slug", "inv-" + category)
                .update();
        UUID product = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:category,:slug,'Inventory product','Test-only product description','lamp','ACTIVE')")
                .param("id", product)
                .param("shop", shopId)
                .param("category", category)
                .param("slug", "inv-" + product)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,50000)")
                .param("id", product)
                .param("shop", shopId)
                .param("code", code + "-" + product)
                .update();
        return product;
    }
}
