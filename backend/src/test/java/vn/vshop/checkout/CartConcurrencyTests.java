package vn.vshop.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.vshop.modules.checkout.service.CartService;

/** Real committed transactions: concurrent cart writes of one buyer must not lose updates. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false"
        })
class CartConcurrencyTests {
    @Autowired JdbcClient jdbc;
    @Autowired CartService carts;
    @MockitoBean JwtDecoder jwtDecoder;

    private final String owner = "concurrency-test-" + UUID.randomUUID();
    private final String buyer = "concurrency-test-" + UUID.randomUUID();
    private UUID shop;
    private UUID category;
    private UUID sku;

    @BeforeEach
    void seed() {
        shop = UUID.randomUUID();
        category = UUID.randomUUID();
        sku = UUID.randomUUID();
        jdbc.sql("INSERT INTO identity.account(external_subject) VALUES (:s)")
                .param("s", owner)
                .update();
        jdbc.sql(
                        "INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,id,'Cart Concurrency','Cart concurrency test shop',:st FROM identity.account WHERE external_subject=:s")
                .param("id", shop)
                .param("st", "APPROVED")
                .param("s", owner)
                .update();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'CartConc',:slug)")
                .param("id", category)
                .param("slug", "cart-conc-" + category)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Concurrent product','Test-only product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "cart-conc-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,50000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "CC-" + sku)
                .update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql(
                        "DELETE FROM checkout.cart_line WHERE cart_id IN (SELECT c.id FROM checkout.cart c JOIN identity.account a ON a.id = c.buyer_account_id WHERE a.external_subject = :b)")
                .param("b", buyer)
                .update();
        jdbc.sql(
                        "DELETE FROM checkout.cart WHERE buyer_account_id IN (SELECT id FROM identity.account WHERE external_subject = :b)")
                .param("b", buyer)
                .update();
        jdbc.sql("DELETE FROM catalog.product_sku WHERE shop_id = :s").param("s", shop).update();
        jdbc.sql("DELETE FROM catalog.product WHERE shop_id = :s").param("s", shop).update();
        jdbc.sql("DELETE FROM catalog.category WHERE id = :c").param("c", category).update();
        jdbc.sql("DELETE FROM merchant.shop WHERE id = :s").param("s", shop).update();
        jdbc.sql("DELETE FROM identity.account WHERE external_subject IN (:a, :b)")
                .param("a", owner)
                .param("b", buyer)
                .update();
    }

    @Test
    void concurrentAddsFromOneBuyerAreNotLost() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Object> add =
                        () -> {
                            ready.countDown();
                            go.await();
                            return carts.addLine(buyer, sku, 1);
                        };
                futures.add(pool.submit(add));
            }
            ready.await();
            go.countDown();
            for (Future<Object> future : futures) {
                future.get(); // surfaces any exception, including a first-cart creation race
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(
                        jdbc.sql(
                                        "SELECT l.quantity FROM checkout.cart_line l JOIN checkout.cart c ON c.id = l.cart_id JOIN identity.account a ON a.id = c.buyer_account_id WHERE a.external_subject = :b")
                                .param("b", buyer)
                                .query(Integer.class)
                                .single())
                .isEqualTo(threads);
        assertThat(
                        jdbc.sql(
                                        "SELECT c.version FROM checkout.cart c JOIN identity.account a ON a.id = c.buyer_account_id WHERE a.external_subject = :b")
                                .param("b", buyer)
                                .query(Long.class)
                                .single())
                .isEqualTo(threads);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM checkout.cart c JOIN identity.account a ON a.id = c.buyer_account_id WHERE a.external_subject = :b")
                                .param("b", buyer)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }
}
