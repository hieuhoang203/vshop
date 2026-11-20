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
class ProductModerationIntegrationTests {
    private static final String OWNER = "moderation-owner";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID shop;
    private UUID category;

    @BeforeEach
    void seed() {
        shop = UUID.randomUUID();
        category = UUID.randomUUID();
        jdbc.sql(
                        "WITH account AS (INSERT INTO identity.account(external_subject) VALUES (:o) RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,account.id,'Moderation Shop','Moderation test shop description',:st FROM account")
                .param("o", OWNER)
                .param("id", shop)
                .param("st", "APPROVED")
                .update();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Moderation',:slug)")
                .param("id", category)
                .param("slug", "moderation-" + category)
                .update();
    }

    @Test
    void adminApprovalPublishesAndRecordsTheReviewedVersion() throws Exception {
        JsonNode product = submittedProduct("Đèn duyệt", "MOD-1");
        UUID id = UUID.fromString(product.get("id").asText());
        String slug = product.get("slug").asText();

        mvc.perform(get("/api/v1/admin/products/review-queue"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/products/review-queue").with(asSeller()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/products/review-queue").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[?(@.id=='" + id + "')].status").value("PENDING_REVIEW"));
        mvc.perform(get("/api/v1/catalog/products/" + slug)).andExpect(status().isNotFound());

        review(id, "APPROVED", "Nội dung phù hợp", 1, 200)
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        mvc.perform(get("/api/v1/catalog/products/" + slug)).andExpect(status().isOk());
        assertThat(
                        jdbc.sql(
                                        "SELECT product_version FROM catalog.product_review WHERE product_id=:p AND decision='APPROVED'")
                                .param("p", id)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE resource_id=:s AND action='ProductApproved' AND actor_subject='moderation-admin'")
                                .param("s", shop)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        mvc.perform(get("/api/v1/admin/products/review-queue").with(asAdmin()))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").isEmpty());
    }

    @Test
    void rejectionNeedsReasonStaysOffSaleAndSellerCanFixAndResubmit() throws Exception {
        JsonNode product = submittedProduct("Bị từ chối", "MOD-2");
        UUID id = UUID.fromString(product.get("id").asText());
        String slug = product.get("slug").asText();

        review(id, "REJECTED", "ngắn", 1, 400);
        review(id, "REJECTED", "Ảnh không đúng sản phẩm", 1, 200)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Ảnh không đúng sản phẩm"));
        mvc.perform(get("/api/v1/catalog/products/" + slug)).andExpect(status().isNotFound());
        mvc.perform(get(base() + "/" + id).with(asOwner()))
                .andExpect(jsonPath("$.rejectionReason").value("Ảnh không đúng sản phẩm"));

        // Editing a rejected product keeps it rejected until it is resubmitted.
        updateContent(id, "Đã sửa ảnh", 2, 200).andExpect(jsonPath("$.status").value("REJECTED"));
        setStatus(id, "PENDING_REVIEW", 3, 200)
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        review(id, "APPROVED", "Đã đạt yêu cầu", 4, 200)
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(
                        jdbc.sql("SELECT count(*) FROM catalog.product_review WHERE product_id=:p")
                                .param("p", id)
                                .query(Long.class)
                                .single())
                .isEqualTo(2);
    }

    @Test
    void decisionIsBoundToTheVersionTheAdminReviewed() throws Exception {
        JsonNode product = submittedProduct("Đổi giữa chừng", "MOD-3");
        UUID id = UUID.fromString(product.get("id").asText());
        // Seller edits while the product waits in the queue.
        updateContent(id, "Nội dung vừa đổi", 1, 200);
        review(id, "APPROVED", "Duyệt bản cũ", 1, 409)
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(productStatus(id)).isEqualTo("PENDING_REVIEW");
        review(id, "APPROVED", "Duyệt bản mới", 2, 200);
        assertThat(productStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void onlyPendingProductsCanBeReviewedAndSellersCannotSelfApprove() throws Exception {
        JsonNode draft = createProduct("Còn nháp", "MOD-4");
        UUID id = UUID.fromString(draft.get("id").asText());
        review(id, "APPROVED", "Chưa gửi duyệt", 0, 409)
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_STATE"));
        review(UUID.randomUUID(), "APPROVED", "Không tồn tại", 0, 404);

        setStatus(id, "PENDING_REVIEW", 0, 200);
        setStatus(id, "ACTIVE", 1, 409); // seller tries to approve own product
        assertThat(productStatus(id)).isEqualTo("PENDING_REVIEW");
        review(id, "APPROVED", "Duyệt lần một", 1, 200);
        review(id, "APPROVED", "Duyệt lần hai", 2, 409);
        review(id, "REJECTED", "Từ chối sau khi duyệt", 2, 409);
        assertThat(productStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void editingLiveContentReturnsItToReviewButPriceChangesDoNot() throws Exception {
        JsonNode product = submittedProduct("Đang bán", "MOD-5");
        UUID id = UUID.fromString(product.get("id").asText());
        String slug = product.get("slug").asText();
        JsonNode live =
                JSON.readTree(
                        review(id, "APPROVED", "Nội dung phù hợp", 1, 200)
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String sku = live.get("skus").get(0).get("id").asText();

        mvc.perform(
                        put(base() + "/" + id + "/skus/" + sku)
                                .with(asOwner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"price\":65000,\"status\":\"ACTIVE\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        assertThat(productStatus(id)).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/catalog/products/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(65000));

        updateContent(id, "Đổi tên sản phẩm", 2, 200)
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mvc.perform(get("/api/v1/catalog/products/" + slug)).andExpect(status().isNotFound());
        review(id, "APPROVED", "Duyệt lại sau chỉnh sửa", 3, 200);
        mvc.perform(get("/api/v1/catalog/products/" + slug)).andExpect(status().isOk());

        // A hidden product keeps its approved content and can be shown again without review.
        setStatus(id, "HIDDEN", 4, 200);
        setStatus(id, "ACTIVE", 5, 200).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private JsonNode submittedProduct(String name, String sellerSku) throws Exception {
        JsonNode created = createProduct(name, sellerSku);
        return JSON.readTree(
                setStatus(UUID.fromString(created.get("id").asText()), "PENDING_REVIEW", 0, 200)
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private JsonNode createProduct(String name, String sellerSku) throws Exception {
        String body =
                "{\"name\":\""
                        + name
                        + "\",\"description\":\"Mô tả sản phẩm kiểm duyệt đủ dài.\",\"categoryId\":\""
                        + category
                        + "\",\"imageKey\":\"lamp\",\"options\":[],\"skus\":[{\"sellerSku\":\""
                        + sellerSku
                        + "\",\"price\":50000,\"optionValues\":{}}]}";
        return JSON.readTree(
                mvc.perform(
                                post(base())
                                        .with(asOwner())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private ResultActions review(
            UUID id, String decision, String reason, long version, int expected) throws Exception {
        return mvc.perform(
                        post("/api/v1/admin/products/" + id + "/review")
                                .with(asAdmin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"decision\":\""
                                                + decision
                                                + "\",\"reason\":\""
                                                + reason
                                                + "\",\"expectedVersion\":"
                                                + version
                                                + "}"))
                .andExpect(status().is(expected));
    }

    private ResultActions setStatus(UUID id, String target, long version, int expected)
            throws Exception {
        return mvc.perform(
                        put(base() + "/" + id + "/status")
                                .with(asOwner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\""
                                                + target
                                                + "\",\"expectedVersion\":"
                                                + version
                                                + "}"))
                .andExpect(status().is(expected));
    }

    private ResultActions updateContent(UUID id, String name, long version, int expected)
            throws Exception {
        return mvc.perform(
                        put(base() + "/" + id)
                                .with(asOwner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\""
                                                + name
                                                + "\",\"description\":\"Mô tả đã cập nhật đủ dài.\",\"categoryId\":\""
                                                + category
                                                + "\",\"imageKey\":\"lamp\",\"expectedVersion\":"
                                                + version
                                                + "}"))
                .andExpect(status().is(expected));
    }

    private String productStatus(UUID id) {
        return jdbc.sql("SELECT status FROM catalog.product WHERE id=:id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private String base() {
        return "/api/v1/seller/shops/" + shop + "/products";
    }

    private static JwtRequestPostProcessor asOwner() {
        return jwt().jwt(j -> j.subject(OWNER))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    private static JwtRequestPostProcessor asSeller() {
        return jwt().jwt(j -> j.subject("not-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    private static JwtRequestPostProcessor asAdmin() {
        return jwt().jwt(j -> j.subject("moderation-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
