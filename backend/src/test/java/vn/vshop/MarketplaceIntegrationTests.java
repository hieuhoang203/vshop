package vn.vshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=true",
            "vshop.worker.poll-ms=86400000"
        })
@AutoConfigureMockMvc
@Transactional
class MarketplaceIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired vn.vshop.modules.merchant.service.ShopService shops;
    @Autowired vn.vshop.modules.platform.worker.OutboxWorker worker;
    @MockitoBean JwtDecoder jwtDecoder;
    private static final String APPLICATION =
            "{\"name\":\"Integration Shop\",\"description\":\"A clearly identified integration test shop for VShop.\"}";

    @Test
    void catalogPriceFiltersAndSortApplyBeforePagination() throws Exception {
        UUID shop = createShop("price-filter-owner", "APPROVED"), category = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Price Test','price-test')")
                .param("id", category)
                .update();
        for (long price : new long[] {190000, 590000, 99000}) {
            UUID product = UUID.randomUUID();
            jdbc.sql(
                            "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:category,:slug,'Price test product','Test-only product description','lamp','ACTIVE')")
                    .param("id", product)
                    .param("shop", shop)
                    .param("category", category)
                    .param("slug", "price-" + product)
                    .update();
            jdbc.sql(
                            "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,:price)")
                    .param("id", product)
                    .param("shop", shop)
                    .param("code", "PRICE-" + product)
                    .param("price", price)
                    .update();
        }
        mvc.perform(
                        get("/api/v1/catalog/products")
                                .param("category", "price-test")
                                .param("sort", "price-asc")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].price").value(99000));
        mvc.perform(
                        get("/api/v1/catalog/products")
                                .param("category", "price-test")
                                .param("sort", "price-desc")
                                .param("size", "1"))
                .andExpect(jsonPath("$.items[0].price").value(590000));
        mvc.perform(
                        get("/api/v1/catalog/products")
                                .param("category", "price-test")
                                .param("minPrice", "100000")
                                .param("maxPrice", "200000"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].price").value(190000));
        mvc.perform(
                        get("/api/v1/catalog/products")
                                .param("minPrice", "500")
                                .param("maxPrice", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/products").param("sort", "unsafe-sort"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void merchantAnalyticsAreAdminOnlyAndBounded() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/shop-activity"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/admin/analytics/shop-activity")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/v1/admin/analytics/shop-activity").param("days", "7").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));
        mvc.perform(get("/api/v1/admin/analytics/shop-activity").param("days", "0").with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/analytics/shop-activity").param("days", "91").with(admin))
                .andExpect(status().isBadRequest());
    }

    private UUID createShop(String subject, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "WITH account AS (INSERT INTO identity.account(external_subject) VALUES (:owner) ON CONFLICT (external_subject) DO UPDATE SET external_subject=EXCLUDED.external_subject RETURNING id) INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,account.id,'Integration Shop','Integration test description only',:status FROM account")
                .param("id", id)
                .param("owner", subject)
                .param("status", status)
                .update();
        return id;
    }

    @Test
    void catalogIsPublicButSellerIsNot() throws Exception {
        mvc.perform(get("/api/v1/catalog/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/seller/shops")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/admin/shops/" + UUID.randomUUID() + "/review")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCanApplyAndDomainAuditOutboxAreAtomic() throws Exception {
        mvc.perform(
                        post("/api/v1/seller/shops")
                                .with(
                                        jwt().jwt(j -> j.subject("apply-buyer"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_BUYER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(APPLICATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE actor_subject='apply-buyer' AND action='ShopSubmitted'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.outbox_event o JOIN platform.audit_event a ON a.id=o.id WHERE a.actor_subject='apply-buyer'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void duplicateApplicationDoesNotCreateAnotherEvent() throws Exception {
        var actor =
                jwt().jwt(j -> j.subject("duplicate-buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
        mvc.perform(
                        post("/api/v1/seller/shops")
                                .with(actor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(APPLICATION))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/v1/seller/shops")
                                .with(actor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(APPLICATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_ALREADY_EXISTS"));
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM merchant.shop WHERE owner_account_id=(SELECT id FROM identity.account WHERE external_subject='duplicate-buyer')")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void buyerCannotReadOrEditAnotherShop() throws Exception {
        UUID id = createShop("another-owner", "SUBMITTED");
        var actor =
                jwt().jwt(j -> j.subject("attacker"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
        mvc.perform(get("/api/v1/seller/shops/" + id).with(actor)).andExpect(status().isNotFound());
        mvc.perform(
                        put("/api/v1/seller/shops/" + id)
                                .with(actor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(APPLICATION))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/seller/shops").with(actor))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void buyerCannotReviewEvenIfCallingApiDirectly() throws Exception {
        UUID id = createShop("review-owner", "SUBMITTED");
        mvc.perform(
                        post("/api/v1/admin/shops/" + id + "/review")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"decision\":\"APPROVED\",\"reason\":\"Test approval\"}"))
                .andExpect(status().isForbidden());
        assertThat(
                        jdbc.sql("SELECT status FROM merchant.shop WHERE id=:id")
                                .param("id", id)
                                .query(String.class)
                                .single())
                .isEqualTo("SUBMITTED");
    }

    @Test
    void adminApprovesOnceAndCannotReviewAgain() throws Exception {
        UUID id = createShop("approval-owner", "SUBMITTED");
        var actor =
                jwt().jwt(j -> j.subject("admin-test"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var command =
                post("/api/v1/admin/shops/" + id + "/review")
                        .with(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"decision\":\"APPROVED\",\"reason\":\"Checked test application\"}");
        mvc.perform(command)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(command).andExpect(status().isConflict());
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE resource_id=:id AND action='ShopApproved'")
                                .param("id", id)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void rejectedShopCanBeResubmittedOnlyByOwner() throws Exception {
        UUID id = createShop("resubmit-owner", "SUBMITTED");
        mvc.perform(
                        post("/api/v1/admin/shops/" + id + "/review")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"decision\":\"REJECTED\",\"reason\":\"Please clarify products\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(
                        put("/api/v1/seller/shops/" + id)
                                .with(
                                        jwt().jwt(j -> j.subject("resubmit-owner"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_BUYER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(APPLICATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void whitespaceNamesAndShortReviewReasonsAreRejected() throws Exception {
        mvc.perform(
                        post("/api/v1/seller/shops")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"  x  \",\"description\":\"Description long enough to pass otherwise\"}"))
                .andExpect(status().isBadRequest());
        UUID id = createShop("validation-owner", "SUBMITTED");
        mvc.perform(
                        post("/api/v1/admin/shops/" + id + "/review")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {"decision":"APPROVED","reason":"     "}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyApprovedShopProductsArePublicAndSearchIsBounded() throws Exception {
        UUID approved = createShop("catalog-approved", "APPROVED"),
                pending = createShop("catalog-pending", "SUBMITTED");
        UUID category = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Integration','integration')")
                .param("id", category)
                .update();
        for (UUID shop : java.util.List.of(approved, pending)) {
            UUID product = UUID.randomUUID();
            jdbc.sql(
                            "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:category,:slug,'Integration product','Test description','lamp','ACTIVE')")
                    .param("id", product)
                    .param("shop", shop)
                    .param("category", category)
                    .param("slug", "product-" + shop)
                    .update();
            jdbc.sql(
                            "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,10000)")
                    .param("id", product)
                    .param("shop", shop)
                    .param("code", "TEST-" + product)
                    .update();
        }
        mvc.perform(get("/api/v1/catalog/products").param("category", "integration"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/catalog/products/product-" + pending))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/products").param("q", "%'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/catalog/products").param("size", "1000"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/products").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outboxReceiptCanReplayWithoutDuplicateEffects() {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO platform.outbox_event(id,event_type,aggregate_id,payload) VALUES (:id,'TestReplay',:aggregate,'{}'::jsonb)")
                .param("id", id)
                .param("aggregate", UUID.randomUUID())
                .update();
        worker.receive();
        jdbc.sql("UPDATE platform.outbox_event SET processed_at=NULL WHERE id=:id")
                .param("id", id)
                .update();
        worker.receive();
        assertThat(
                        jdbc.sql("SELECT count(*) FROM platform.event_receipt WHERE event_id=:id")
                                .param("id", id)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(
                        jdbc.sql(
                                        "SELECT processed_at IS NOT NULL FROM platform.outbox_event WHERE id=:id")
                                .param("id", id)
                                .query(Boolean.class)
                                .single())
                .isTrue();
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentApplicationsCreateOneShopAndOneAuditEvent() throws Exception {
        String subject = "concurrency-test-" + UUID.randomUUID();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt =
                    () -> {
                        ready.countDown();
                        start.await();
                        try {
                            shops.submit(
                                    subject,
                                    "Concurrency Test Shop",
                                    "Synthetic test subject in isolated test database.");
                            return true;
                        } catch (vn.vshop.common.exception.BusinessException e) {
                            if (!e.code().equals("SHOP_ALREADY_EXISTS")) throw e;
                            return false;
                        }
                    };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                            java.util.List.of(
                                    first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM merchant.shop WHERE owner_account_id=(SELECT id FROM identity.account WHERE external_subject=:owner)")
                                .param("owner", subject)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.audit_event WHERE actor_subject=:owner")
                                .param("owner", subject)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }
}
