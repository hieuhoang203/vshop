package vn.vshop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class SellerCatalogIntegrationTests {
    private static final String OWNER = "catalog-owner";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID shop;
    private UUID category;

    @BeforeEach
    void seed() {
        shop = createShop(OWNER, "APPROVED");
        category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Seller Test',:slug)")
                .param("id", category)
                .param("slug", "seller-test-" + category)
                .update();
    }

    @Test
    void sellerCreatesVariantProductPublishesAndBuyerSeesLowestActivePrice() throws Exception {
        JsonNode created = createProduct(OWNER, variantBody("Áo thun Đẹp"), 201);
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("slug").asText()).startsWith("ao-thun-dep-");
        assertThat(created.get("skus")).hasSize(3);
        assertThat(created.get("options")).hasSize(2);
        UUID product = UUID.fromString(created.get("id").asText());
        String slug = created.get("slug").asText();

        mvc.perform(get("/api/v1/catalog/products/" + slug)).andExpect(status().isNotFound());

        JsonNode published = changeStatus(OWNER, product, "ACTIVE", 0, 200);
        assertThat(published.get("status").asText()).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/catalog/products/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(150000));

        JsonNode sku = skuBySellerCode(published, "TEE-RED-S");
        JsonNode repriced =
                updateSku(OWNER, product, sku.get("id").asText(), 99000, "ACTIVE", 0, 200);
        assertThat(skuBySellerCode(repriced, "TEE-RED-S").get("version").asLong()).isEqualTo(1);
        mvc.perform(get("/api/v1/catalog/products/" + slug))
                .andExpect(jsonPath("$.price").value(99000));

        updateSku(OWNER, product, sku.get("id").asText(), 99000, "INACTIVE", 1, 200);
        mvc.perform(get("/api/v1/catalog/products/" + slug))
                .andExpect(jsonPath("$.price").value(160000));

        changeStatus(OWNER, product, "HIDDEN", 1, 200);
        mvc.perform(get("/api/v1/catalog/products/" + slug)).andExpect(status().isNotFound());
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE actor_subject=:a AND resource_id=:s")
                                .param("a", OWNER)
                                .param("s", shop)
                                .query(Long.class)
                                .single())
                .isEqualTo(5);
    }

    @Test
    void anotherSellerCannotTouchProductsOfTheShop() throws Exception {
        JsonNode created = createProduct(OWNER, variantBody("Isolation"), 201);
        UUID product = UUID.fromString(created.get("id").asText());
        createShop("other-owner", "APPROVED");
        String base = "/api/v1/seller/shops/" + shop + "/products";
        mvc.perform(get(base).with(actor("other-owner"))).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + product).with(actor("other-owner")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(base)
                                .with(actor("other-owner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(variantBody("Intruder")))
                .andExpect(status().isNotFound());
        changeStatus("other-owner", product, "ACTIVE", 0, 404);
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
    }

    @Test
    void productOfAnotherShopIsNotReachableThroughOwnShopPath() throws Exception {
        UUID otherShop = createShop("second-owner", "APPROVED");
        JsonNode foreign =
                JSON.readTree(
                        mvc.perform(
                                        post("/api/v1/seller/shops/" + otherShop + "/products")
                                                .with(actor("second-owner"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(variantBody("Foreign")))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        mvc.perform(
                        get("/api/v1/seller/shops/"
                                        + shop
                                        + "/products/"
                                        + foreign.get("id").asText())
                                .with(actor(OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shopMustBeApprovedBeforeManagingProducts() throws Exception {
        UUID pending = createShop("pending-owner", "SUBMITTED");
        mvc.perform(
                        post("/api/v1/seller/shops/" + pending + "/products")
                                .with(actor("pending-owner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(variantBody("Not yet")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SHOP_STATE"));
    }

    @Test
    void invalidVariantsAndInputAreRejectedWithoutPartialWrites() throws Exception {
        long before = count("catalog.product");
        // SKU missing one option dimension.
        createProduct(
                OWNER,
                body(
                        "Thiếu",
                        "[{\"code\":\"size\",\"name\":\"Size\",\"values\":[{\"code\":\"s\",\"label\":\"S\"}]}]",
                        "[{\"sellerSku\":\"BAD-1\",\"price\":1000,\"optionValues\":{}}]"),
                400);
        // Unknown value code.
        createProduct(
                OWNER,
                body(
                        "Sai giá trị",
                        "[{\"code\":\"size\",\"name\":\"Size\",\"values\":[{\"code\":\"s\",\"label\":\"S\"}]}]",
                        "[{\"sellerSku\":\"BAD-2\",\"price\":1000,\"optionValues\":{\"size\":\"xl\"}}]"),
                400);
        // Two SKUs with the same combination.
        createProduct(
                OWNER,
                body(
                        "Trùng tổ hợp",
                        "[{\"code\":\"size\",\"name\":\"Size\",\"values\":[{\"code\":\"s\",\"label\":\"S\"}]}]",
                        "[{\"sellerSku\":\"D-1\",\"price\":1000,\"optionValues\":{\"size\":\"s\"}},"
                                + "{\"sellerSku\":\"D-2\",\"price\":1000,\"optionValues\":{\"size\":\"s\"}}]"),
                409);
        // Same seller SKU twice.
        createProduct(
                OWNER,
                body(
                        "Trùng mã",
                        "[{\"code\":\"size\",\"name\":\"Size\",\"values\":[{\"code\":\"s\",\"label\":\"S\"},{\"code\":\"m\",\"label\":\"M\"}]}]",
                        "[{\"sellerSku\":\"X-1\",\"price\":1000,\"optionValues\":{\"size\":\"s\"}},"
                                + "{\"sellerSku\":\"x-1\",\"price\":1000,\"optionValues\":{\"size\":\"m\"}}]"),
                409);
        // No variants but two SKUs.
        createProduct(
                OWNER,
                body(
                        "Hai SKU",
                        "[]",
                        "[{\"sellerSku\":\"N-1\",\"price\":1000,\"optionValues\":{}},"
                                + "{\"sellerSku\":\"N-2\",\"price\":1000,\"optionValues\":{}}]"),
                400);
        // Price must be positive integer VND within bounds.
        createProduct(
                OWNER,
                body("Giá 0", "[]", "[{\"sellerSku\":\"P-0\",\"price\":0,\"optionValues\":{}}]"),
                400);
        // Decimal price is rejected rather than rounded.
        createProduct(
                OWNER,
                body(
                        "Giá lẻ",
                        "[]",
                        "[{\"sellerSku\":\"P-1\",\"price\":1000.5,\"optionValues\":{}}]"),
                400);
        // Unknown category and unknown image.
        createProduct(
                OWNER,
                variantBody("Danh mục lạ")
                        .replace(category.toString(), UUID.randomUUID().toString()),
                400);
        createProduct(OWNER, variantBody("Ảnh lạ").replace("\"lamp\"", "\"../evil\""), 400);
        assertThat(count("catalog.product")).isEqualTo(before);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM catalog.product_sku WHERE seller_sku IN ('BAD-1','BAD-2','D-1','D-2','X-1','x-1','N-1','N-2')")
                                .query(Long.class)
                                .single())
                .isZero();
    }

    @Test
    void sellerSkuCodeIsUniquePerShopAcrossProducts() throws Exception {
        createProduct(OWNER, simpleBody("Một", "SAME-CODE"), 201);
        createProduct(OWNER, simpleBody("Hai", "SAME-CODE"), 409);
        // Another shop may reuse the code.
        UUID otherShop = createShop("code-reuse-owner", "APPROVED");
        mvc.perform(
                        post("/api/v1/seller/shops/" + otherShop + "/products")
                                .with(actor("code-reuse-owner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(simpleBody("Ba ba", "SAME-CODE")))
                .andExpect(status().isCreated());
    }

    @Test
    void addingSkuValidatesAgainstExistingOptionsAndRejectsDuplicates() throws Exception {
        JsonNode created = createProduct(OWNER, variantBody("Thêm SKU"), 201);
        UUID product = UUID.fromString(created.get("id").asText());
        String path = "/api/v1/seller/shops/" + shop + "/products/" + product + "/skus";
        String added =
                "{\"sellerSku\":\"TEE-BLUE-S\",\"price\":175000,\"optionValues\":{\"color\":\"blue\",\"size\":\"s\"}}";
        mvc.perform(
                        post(path)
                                .with(actor(OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(added))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skus.length()").value(4));
        mvc.perform(
                        post(path)
                                .with(actor(OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(added.replace("TEE-BLUE-S", "TEE-BLUE-S2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
        mvc.perform(
                        post(path)
                                .with(actor(OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sellerSku\":\"TEE-GREEN\",\"price\":1000,\"optionValues\":{\"color\":\"green\",\"size\":\"s\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lastActiveSkuOfVisibleProductCannotBeDeactivatedAndNeedsActiveSkuToPublish()
            throws Exception {
        JsonNode created = createProduct(OWNER, simpleBody("Đơn", "ONLY-ONE"), 201);
        UUID product = UUID.fromString(created.get("id").asText());
        String sku = created.get("skus").get(0).get("id").asText();
        // Deactivate while DRAFT is allowed; then publishing must fail.
        updateSku(OWNER, product, sku, 50000, "INACTIVE", 0, 200);
        changeStatus(OWNER, product, "ACTIVE", 0, 409);
        updateSku(OWNER, product, sku, 50000, "ACTIVE", 1, 200);
        changeStatus(OWNER, product, "ACTIVE", 0, 200);
        updateSku(OWNER, product, sku, 50000, "INACTIVE", 2, 409);
        assertThat(
                        jdbc.sql("SELECT status FROM catalog.product_sku WHERE id=:id")
                                .param("id", UUID.fromString(sku))
                                .query(String.class)
                                .single())
                .isEqualTo("ACTIVE");
    }

    @Test
    void staleVersionsAreRejectedAndStatusMachineIsEnforced() throws Exception {
        JsonNode created = createProduct(OWNER, simpleBody("Phiên bản", "VER-1"), 201);
        UUID product = UUID.fromString(created.get("id").asText());
        String path = "/api/v1/seller/shops/" + shop + "/products/" + product;
        String update =
                "{\"name\":\"Tên mới\",\"description\":\"Mô tả mới đủ dài\",\"categoryId\":\""
                        + category
                        + "\",\"imageKey\":\"mug\",\"expectedVersion\":%d}";
        mvc.perform(
                        put(path)
                                .with(actor(OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(update.formatted(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.imageKey").value("mug"));
        mvc.perform(
                        put(path)
                                .with(actor(OWNER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(update.formatted(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        changeStatus(OWNER, product, "HIDDEN", 1, 409);
        changeStatus(OWNER, product, "DRAFT", 1, 409);
        changeStatus(OWNER, product, "ACTIVE", 0, 409);
        changeStatus(OWNER, product, "ACTIVE", 1, 200);
        changeStatus(OWNER, product, "ACTIVE", 2, 409);
    }

    @Test
    void listIsScopedToShopAndShowsStockFromInventory() throws Exception {
        JsonNode created = createProduct(OWNER, simpleBody("Có tồn", "STOCKED"), 201);
        UUID sku = UUID.fromString(created.get("skus").get(0).get("id").asText());
        UUID warehouse = UUID.randomUUID();
        jdbc.sql("INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:s,'W','Kho')")
                .param("id", warehouse)
                .param("s", shop)
                .update();
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand,reserved) VALUES (:k,:w,:s,10,3)")
                .param("k", sku)
                .param("w", warehouse)
                .param("s", shop)
                .update();
        mvc.perform(
                        get("/api/v1/seller/shops/" + shop + "/products")
                                .with(actor(OWNER))
                                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].skus[0].available").value(7));
        mvc.perform(
                        get("/api/v1/seller/shops/" + shop + "/products")
                                .with(actor(OWNER))
                                .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode createProduct(String subject, String body, int expectedStatus)
            throws Exception {
        ResultActions result =
                mvc.perform(
                        post("/api/v1/seller/shops/" + shop + "/products")
                                .with(actor(subject))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body));
        result.andExpect(status().is(expectedStatus));
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode changeStatus(
            String subject, UUID product, String target, long version, int expectedStatus)
            throws Exception {
        ResultActions result =
                mvc.perform(
                        put("/api/v1/seller/shops/" + shop + "/products/" + product + "/status")
                                .with(actor(subject))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\""
                                                + target
                                                + "\",\"expectedVersion\":"
                                                + version
                                                + "}"));
        result.andExpect(status().is(expectedStatus));
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode updateSku(
            String subject,
            UUID product,
            String sku,
            long price,
            String skuStatus,
            long version,
            int expectedStatus)
            throws Exception {
        ResultActions result =
                mvc.perform(
                        put("/api/v1/seller/shops/"
                                        + shop
                                        + "/products/"
                                        + product
                                        + "/skus/"
                                        + sku)
                                .with(actor(subject))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"price\":"
                                                + price
                                                + ",\"status\":\""
                                                + skuStatus
                                                + "\",\"expectedVersion\":"
                                                + version
                                                + "}"));
        result.andExpect(status().is(expectedStatus));
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JsonNode skuBySellerCode(JsonNode product, String code) {
        for (JsonNode sku : product.get("skus")) {
            if (code.equals(sku.get("sellerSku").asText())) {
                return sku;
            }
        }
        throw new AssertionError("SKU not found: " + code);
    }

    private String variantBody(String name) {
        return body(
                name,
                "[{\"code\":\"color\",\"name\":\"Màu\",\"values\":[{\"code\":\"red\",\"label\":\"Đỏ\"},{\"code\":\"blue\",\"label\":\"Xanh\"}]},"
                        + "{\"code\":\"size\",\"name\":\"Size\",\"values\":[{\"code\":\"s\",\"label\":\"S\"},{\"code\":\"m\",\"label\":\"M\"}]}]",
                "[{\"sellerSku\":\"TEE-RED-S\",\"price\":150000,\"optionValues\":{\"color\":\"red\",\"size\":\"s\"}},"
                        + "{\"sellerSku\":\"TEE-RED-M\",\"price\":160000,\"optionValues\":{\"color\":\"red\",\"size\":\"m\"}},"
                        + "{\"sellerSku\":\"TEE-BLUE-M\",\"price\":170000,\"optionValues\":{\"color\":\"blue\",\"size\":\"m\"}}]");
    }

    private String simpleBody(String name, String sellerSku) {
        return body(
                name,
                "[]",
                "[{\"sellerSku\":\"" + sellerSku + "\",\"price\":50000,\"optionValues\":{}}]");
    }

    private String body(String name, String options, String skus) {
        return "{\"name\":\""
                + name
                + "\",\"description\":\"Mô tả sản phẩm kiểm thử đủ dài.\",\"categoryId\":\""
                + category
                + "\",\"imageKey\":\"lamp\",\"options\":"
                + options
                + ",\"skus\":"
                + skus
                + "}";
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
                        "WITH account AS (INSERT INTO identity.account(external_subject) VALUES (:owner) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,account.id,'Catalog Shop','Catalog test shop description only',:status FROM account")
                .param("id", id)
                .param("owner", subject)
                .param("status", shopStatus)
                .update();
        return id;
    }
}
