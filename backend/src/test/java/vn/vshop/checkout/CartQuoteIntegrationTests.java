package vn.vshop.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CartQuoteIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID shopA;
    private UUID shopB;
    private UUID category;
    private UUID skuA1; // shop A, 100.000, stock 10
    private UUID skuA2; // shop A, 250.000, stock 2
    private UUID skuB1; // shop B, 40.000, no stock

    @BeforeEach
    void seed() {
        category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Cart Test',:slug)")
                .param("id", category)
                .param("slug", "cart-" + category)
                .update();
        shopA = createShop("cart-seller-a", "APPROVED", "Shop A");
        shopB = createShop("cart-seller-b", "APPROVED", "Shop B");
        skuA1 = createSku(shopA, "ACTIVE", "A1", 100_000, 10);
        skuA2 = createSku(shopA, "ACTIVE", "A2", 250_000, 2);
        skuB1 = createSku(shopB, "ACTIVE", "B1", 40_000, 0);
    }

    @Test
    void cartGroupsByShopAndPricesFromTheCatalogNeverFromTheClient() throws Exception {
        mvc.perform(get("/api/v1/cart").with(buyer("buyer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.canCheckout").value(false))
                .andExpect(jsonPath("$.notCalculated[0]").value("SHIPPING"));
        // A client-sent price is ignored: the API has no price field to send.
        mvc.perform(
                        post("/api/v1/cart/lines")
                                .with(buyer("buyer-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"skuId\":\""
                                                + skuA1
                                                + "\",\"quantity\":3,\"unitPrice\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].lines[0].unitPrice").value(100_000))
                .andExpect(jsonPath("$.groups[0].lines[0].lineTotal").value(300_000));
        add("buyer-1", skuA2, 1);
        JsonNode cart = add("buyer-1", skuB1, 2);

        assertThat(cart.get("groups")).hasSize(2);
        assertThat(cart.get("itemCount").asInt()).isEqualTo(6);
        assertThat(cart.get("version").asLong()).isEqualTo(3);
        JsonNode groupA = cart.get("groups").get(0);
        assertThat(groupA.get("shopName").asText()).isEqualTo("Shop A");
        assertThat(groupA.get("subtotal").asLong()).isEqualTo(550_000);
        // Shop B has no stock: line is flagged and excluded from totals.
        JsonNode lineB = cart.get("groups").get(1).get("lines").get(0);
        assertThat(lineB.get("issue").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(cart.get("groups").get(1).get("subtotal").asLong()).isZero();
        assertThat(cart.get("itemsTotal").asLong()).isEqualTo(550_000);
        assertThat(cart.get("canCheckout").asBoolean()).isFalse();

        // The catalog price changes; the cart follows without any client involvement.
        jdbc.sql("UPDATE catalog.product_sku SET price = 120000 WHERE id = :id")
                .param("id", skuA1)
                .update();
        mvc.perform(get("/api/v1/cart").with(buyer("buyer-1")))
                .andExpect(jsonPath("$.groups[0].lines[0].unitPrice").value(120_000))
                .andExpect(jsonPath("$.itemsTotal").value(610_000));
    }

    @Test
    void stockIssuesAreReportedPerLineWithoutHoldingStock() throws Exception {
        add("buyer-2", skuA1, 4);
        JsonNode cart = add("buyer-2", skuA2, 5); // only 2 sellable
        JsonNode lines = cart.get("groups").get(0).get("lines");
        assertThat(lines.get(0).get("issue").isNull()).isTrue();
        assertThat(lines.get(1).get("issue").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(lines.get(1).get("availableQuantity").asLong()).isEqualTo(2);
        assertThat(cart.get("itemsTotal").asLong()).isEqualTo(400_000);
        assertThat(cart.get("canCheckout").asBoolean()).isFalse();
        // Nothing was reserved by adding to the cart.
        assertThat(
                        jdbc.sql(
                                        "SELECT coalesce(sum(reserved),0) FROM inventory.stock_balance WHERE sku_id IN (:a,:b)")
                                .param("a", skuA1)
                                .param("b", skuA2)
                                .query(Long.class)
                                .single())
                .isZero();
        mvc.perform(
                        put("/api/v1/cart/lines/" + skuA2)
                                .with(buyer("buyer-2"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":2}"))
                .andExpect(jsonPath("$.canCheckout").value(true))
                .andExpect(jsonPath("$.itemsTotal").value(900_000));
    }

    @Test
    void unsellableSkusCannotBeAddedAndExistingLinesBecomeUnavailable() throws Exception {
        UUID hidden = createSku(shopA, "ACTIVE", "HID", 10_000, 5);
        jdbc.sql("UPDATE catalog.product SET status='HIDDEN' WHERE id = :id")
                .param("id", hidden)
                .update();
        UUID pendingShop = createShop("cart-seller-c", "SUBMITTED", "Pending Shop");
        UUID fromPending = createSku(pendingShop, "ACTIVE", "PEND", 10_000, 5);
        UUID inactiveSku = createSku(shopA, "INACTIVE", "OFF", 10_000, 5);
        for (UUID sku : new UUID[] {hidden, fromPending, inactiveSku}) {
            mvc.perform(
                            post("/api/v1/cart/lines")
                                    .with(buyer("buyer-3"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"skuId\":\"" + sku + "\",\"quantity\":1}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SKU_UNAVAILABLE"));
        }
        mvc.perform(
                        post("/api/v1/cart/lines")
                                .with(buyer("buyer-3"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"skuId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isNotFound());

        add("buyer-3", skuA1, 1);
        jdbc.sql(
                        "UPDATE merchant.shop SET status='REJECTED', rejection_reason='Hết hiệu lực test' WHERE id = :id")
                .param("id", shopA)
                .update();
        mvc.perform(get("/api/v1/cart").with(buyer("buyer-3")))
                .andExpect(jsonPath("$.groups[0].lines[0].issue").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.itemsTotal").value(0))
                .andExpect(jsonPath("$.canCheckout").value(false));
    }

    @Test
    void quantityAndSizeLimitsAreEnforced() throws Exception {
        for (int quantity : new int[] {0, -1, 100}) {
            mvc.perform(
                            post("/api/v1/cart/lines")
                                    .with(buyer("buyer-4"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"skuId\":\""
                                                    + skuA1
                                                    + "\",\"quantity\":"
                                                    + quantity
                                                    + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        put("/api/v1/cart/lines/" + skuA1)
                                .with(buyer("buyer-4"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":100}"))
                .andExpect(status().isBadRequest());
        // Adding past the cap clamps at 99 instead of failing or overflowing.
        add("buyer-4", skuA1, 90);
        add("buyer-4", skuA1, 90).get("groups").get(0).get("lines").get(0).get("quantity").asInt();
        assertThat(
                        jdbc.sql("SELECT quantity FROM checkout.cart_line WHERE sku_id = :s")
                                .param("s", skuA1)
                                .query(Integer.class)
                                .single())
                .isEqualTo(99);
        // At most 50 distinct lines per cart.
        for (int i = 0; i < 49; i++) {
            add("buyer-4", createSku(shopB, "ACTIVE", "MANY" + i, 1_000, 1), 1);
        }
        mvc.perform(
                        post("/api/v1/cart/lines")
                                .with(buyer("buyer-4"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"skuId\":\"" + skuB1 + "\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CART_INPUT"));
    }

    @Test
    void moneyStaysExactAtTheUpperBounds() throws Exception {
        UUID dear = createSku(shopA, "ACTIVE", "DEAR", 1_000_000_000L, 99);
        add("buyer-5", dear, 99).get("itemsTotal");
        mvc.perform(get("/api/v1/cart").with(buyer("buyer-5")))
                .andExpect(jsonPath("$.itemsTotal").value(99_000_000_000L))
                .andExpect(jsonPath("$.groups[0].lines[0].lineTotal").value(99_000_000_000L));
    }

    @Test
    void cartsAreIsolatedAndRolesAreEnforced() throws Exception {
        add("buyer-6", skuA1, 2);
        mvc.perform(get("/api/v1/cart").with(buyer("buyer-7")))
                .andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/cart")
                                .with(
                                        jwt().jwt(j -> j.subject("an-admin"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/checkout/quotes")).andExpect(status().isUnauthorized());
    }

    @Test
    void removeAndClearBumpTheVersionOnlyWhenSomethingChanged() throws Exception {
        add("buyer-8", skuA1, 1);
        JsonNode cart = add("buyer-8", skuA2, 1);
        long version = cart.get("version").asLong();
        mvc.perform(delete("/api/v1/cart/lines/" + skuB1).with(buyer("buyer-8")))
                .andExpect(jsonPath("$.version").value(version));
        mvc.perform(delete("/api/v1/cart/lines/" + skuA1).with(buyer("buyer-8")))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.itemCount").value(1));
        // Setting the quantity it already has changes nothing.
        mvc.perform(
                        put("/api/v1/cart/lines/" + skuA2)
                                .with(buyer("buyer-8"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1}"))
                .andExpect(jsonPath("$.version").value(version + 1));
        mvc.perform(delete("/api/v1/cart").with(buyer("buyer-8")))
                .andExpect(jsonPath("$.version").value(version + 2))
                .andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(delete("/api/v1/cart").with(buyer("buyer-8")))
                .andExpect(jsonPath("$.version").value(version + 2));
    }

    @Test
    void quoteIsAnImmutableExpiringSnapshotBoundToTheCartVersion() throws Exception {
        mvc.perform(post("/api/v1/checkout/quotes").with(buyer("buyer-9")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        add("buyer-9", skuA1, 2);
        JsonNode quote =
                JSON.readTree(
                        mvc.perform(post("/api/v1/checkout/quotes").with(buyer("buyer-9")))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.current").value(true))
                                .andExpect(jsonPath("$.expired").value(false))
                                .andExpect(jsonPath("$.content.itemsTotal").value(200_000))
                                .andExpect(jsonPath("$.content.canCheckout").value(true))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String id = quote.get("id").asText();
        assertThat(
                        jdbc.sql(
                                        "SELECT expires_at > now() + interval '9 minutes' AND expires_at < now() + interval '11 minutes' FROM checkout.quote WHERE id = :id")
                                .param("id", UUID.fromString(id))
                                .query(Boolean.class)
                                .single())
                .isTrue();

        // A later catalog price change does not alter what was quoted.
        jdbc.sql("UPDATE catalog.product_sku SET price = 999999 WHERE id = :id")
                .param("id", skuA1)
                .update();
        mvc.perform(get("/api/v1/checkout/quotes/" + id).with(buyer("buyer-9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.content.itemsTotal").value(200_000))
                .andExpect(jsonPath("$.content.groups[0].lines[0].unitPrice").value(100_000));

        // Changing the cart makes the quote stale.
        add("buyer-9", skuA2, 1);
        mvc.perform(get("/api/v1/checkout/quotes/" + id).with(buyer("buyer-9")))
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.expired").value(false));

        // Expiry also makes it stale.
        jdbc.sql(
                        "UPDATE checkout.quote SET created_at = now() - interval '20 minutes', expires_at = now() - interval '5 seconds' WHERE id = :id")
                .param("id", UUID.fromString(id))
                .update();
        mvc.perform(get("/api/v1/checkout/quotes/" + id).with(buyer("buyer-9")))
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.current").value(false));

        // Another buyer cannot read it; it looks the same as a missing quote.
        mvc.perform(get("/api/v1/checkout/quotes/" + id).with(buyer("buyer-10")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        mvc.perform(get("/api/v1/checkout/quotes/" + UUID.randomUUID()).with(buyer("buyer-9")))
                .andExpect(status().isNotFound());
    }

    @Test
    void quoteRecordsIssuesInsteadOfHidingThem() throws Exception {
        add("buyer-11", skuA1, 1);
        add("buyer-11", skuB1, 1); // no stock
        mvc.perform(post("/api/v1/checkout/quotes").with(buyer("buyer-11")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content.canCheckout").value(false))
                .andExpect(jsonPath("$.content.itemsTotal").value(100_000))
                .andExpect(jsonPath("$.content.groups[1].lines[0].issue").value("OUT_OF_STOCK"));
    }

    @Test
    void visibleProductsListTheirBuyableSkusPublicly() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/cart-" + skuA1 + "/skus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(skuA1.toString()))
                .andExpect(jsonPath("$[0].price").value(100_000))
                .andExpect(jsonPath("$[0].available").value(10));
        jdbc.sql("UPDATE catalog.product SET status='HIDDEN' WHERE id = :id")
                .param("id", skuA1)
                .update();
        mvc.perform(get("/api/v1/catalog/products/cart-" + skuA1 + "/skus"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/products/does-not-exist/skus"))
                .andExpect(status().isNotFound());
    }

    private JsonNode add(String subject, UUID skuId, int quantity) throws Exception {
        ResultActions result =
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
                        .andExpect(status().isOk());
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JwtRequestPostProcessor buyer(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    private UUID createShop(String owner, String shopStatus, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "WITH account AS (INSERT INTO identity.account(external_subject) VALUES (:o) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,account.id,:name,'Cart test shop description only',:st FROM account")
                .param("o", owner)
                .param("id", id)
                .param("name", name)
                .param("st", shopStatus)
                .update();
        return id;
    }

    /** Creates a single-SKU product; the product row id doubles as the SKU id. */
    private UUID createSku(UUID shop, String skuStatus, String code, long price, long stock) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,:name,'Cart test product description','lamp','ACTIVE')")
                .param("id", id)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "cart-" + id)
                .param("name", "Product " + code)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price,status) VALUES (:id,:id,:shop,:code,:price,:st)")
                .param("id", id)
                .param("shop", shop)
                .param("code", code + "-" + id)
                .param("price", price)
                .param("st", skuStatus)
                .update();
        if (stock > 0) {
            UUID warehouse = UUID.randomUUID();
            jdbc.sql(
                            "INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:shop,:code,'Kho') ON CONFLICT (shop_id, code) DO NOTHING")
                    .param("id", warehouse)
                    .param("shop", shop)
                    .param("code", "W-" + id)
                    .update();
            jdbc.sql(
                            "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand) SELECT :sku, w.id, :shop, :q FROM inventory.warehouse w WHERE w.id = :wh")
                    .param("sku", id)
                    .param("shop", shop)
                    .param("q", stock)
                    .param("wh", warehouse)
                    .update();
        }
        return id;
    }
}
