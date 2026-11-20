package vn.vshop.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
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
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.inventory.entity.ReservationStatus;
import vn.vshop.modules.inventory.entity.StockReservation;
import vn.vshop.modules.inventory.service.ReservationService;

/**
 * Reservation lifecycle on real, committed transactions (no test-level rollback) so row locks,
 * rollbacks of failed commands and races between threads behave as in production. Uses its own shop
 * and removes it afterwards.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false"
        })
class ReservationConcurrencyTests {
    @Autowired JdbcClient jdbc;
    @Autowired ReservationService reservations;
    @MockitoBean JwtDecoder jwtDecoder;

    private final String subject = "concurrency-test-" + UUID.randomUUID();
    private UUID shop;
    private UUID category;
    private UUID sku;
    private UUID warehouse;

    @BeforeEach
    void seed() {
        shop = UUID.randomUUID();
        category = UUID.randomUUID();
        sku = UUID.randomUUID();
        warehouse = UUID.randomUUID();
        jdbc.sql("INSERT INTO identity.account(external_subject) VALUES (:s)")
                .param("s", subject)
                .update();
        jdbc.sql(
                        "INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,id,'Concurrency Shop','Concurrency test shop description',:st FROM identity.account WHERE external_subject=:s")
                .param("id", shop)
                .param("st", "APPROVED")
                .param("s", subject)
                .update();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Concurrency',:slug)")
                .param("id", category)
                .param("slug", "conc-" + category)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Concurrency product','Test-only product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "conc-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,50000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "CONC-" + sku)
                .update();
        jdbc.sql("INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:s,'W','Kho')")
                .param("id", warehouse)
                .param("s", shop)
                .update();
    }

    @AfterEach
    void cleanUp() {
        String bySku = "sku_id IN (SELECT id FROM catalog.product_sku WHERE shop_id = :shop)";
        for (String table :
                List.of(
                        "inventory.stock_movement",
                        "inventory.stock_reservation",
                        "inventory.stock_balance")) {
            jdbc.sql("DELETE FROM " + table + " WHERE " + bySku).param("shop", shop).update();
        }
        jdbc.sql("DELETE FROM inventory.warehouse WHERE shop_id = :shop")
                .param("shop", shop)
                .update();
        jdbc.sql("DELETE FROM catalog.product_sku WHERE shop_id = :shop")
                .param("shop", shop)
                .update();
        jdbc.sql("DELETE FROM catalog.product WHERE shop_id = :shop").param("shop", shop).update();
        jdbc.sql("DELETE FROM catalog.category WHERE id = :id").param("id", category).update();
        jdbc.sql("DELETE FROM merchant.shop WHERE id = :shop").param("shop", shop).update();
        jdbc.sql("DELETE FROM identity.account WHERE external_subject = :s")
                .param("s", subject)
                .update();
    }

    @Test
    void lastUnitGoesToExactlyOneOfManyConcurrentBuyers() throws Exception {
        stock(1);
        List<Callable<Object>> buyers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String key = "last-unit-" + i + "-" + sku;
            buyers.add(
                    () -> {
                        try {
                            return reservations.reserve(
                                    sku, 1, UUID.randomUUID(), key, Duration.ofMinutes(15));
                        } catch (BusinessException e) {
                            return e;
                        }
                    });
        }
        List<Object> results = runTogether(buyers);
        long won = results.stream().filter(StockReservation.class::isInstance).count();
        long lost =
                results.stream()
                        .filter(
                                r ->
                                        r instanceof BusinessException e
                                                && e.errorCode() == ErrorCode.INSUFFICIENT_STOCK)
                        .count();
        assertThat(won).isEqualTo(1);
        assertThat(lost).isEqualTo(7);
        assertThat(balance("reserved")).isEqualTo(1);
        assertThat(count("inventory.stock_reservation")).isEqualTo(1);
        assertThat(count("inventory.stock_movement WHERE movement_type = 'RESERVE'")).isEqualTo(1);
    }

    @Test
    void stockIsNeverOversoldAcrossManyQuantities() throws Exception {
        stock(10);
        List<Callable<Object>> buyers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String key = "many-" + i + "-" + sku;
            buyers.add(
                    () -> {
                        try {
                            return reservations.reserve(
                                    sku, 3, UUID.randomUUID(), key, Duration.ofMinutes(15));
                        } catch (BusinessException e) {
                            return e;
                        }
                    });
        }
        long won = runTogether(buyers).stream().filter(StockReservation.class::isInstance).count();
        assertThat(won).isEqualTo(3); // 10 units / 3 per buyer
        assertThat(balance("reserved")).isEqualTo(9);
        assertThat(balance("on_hand")).isEqualTo(10);
    }

    @Test
    void concurrentRetriesOfTheSameKeyReserveOnce() throws Exception {
        stock(10);
        UUID reference = UUID.randomUUID();
        String key = "same-key-" + sku;
        List<Callable<Object>> retries = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            retries.add(() -> reservations.reserve(sku, 2, reference, key, Duration.ofMinutes(15)));
        }
        List<Object> results = runTogether(retries);
        assertThat(results).allMatch(StockReservation.class::isInstance);
        assertThat(results.stream().map(r -> ((StockReservation) r).id()).distinct()).hasSize(1);
        assertThat(balance("reserved")).isEqualTo(2);
        assertThat(count("inventory.stock_movement WHERE movement_type = 'RESERVE'")).isEqualTo(1);
    }

    @Test
    void concurrentCommitsConsumeStockOnce() throws Exception {
        stock(10);
        StockReservation held =
                reservations.reserve(
                        sku, 4, UUID.randomUUID(), "commit-" + sku, Duration.ofMinutes(15));
        List<Callable<Object>> commits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            commits.add(() -> reservations.commit(held.id()));
        }
        assertThat(runTogether(commits)).allMatch(StockReservation.class::isInstance);
        assertThat(balance("on_hand")).isEqualTo(6);
        assertThat(balance("reserved")).isZero();
        assertThat(count("inventory.stock_movement WHERE movement_type = 'COMMIT'")).isEqualTo(1);
    }

    @Test
    void lifecycleIsIdempotentAndTerminalStatesStayTerminal() {
        stock(10);
        UUID reference = UUID.randomUUID();
        StockReservation first =
                reservations.reserve(sku, 3, reference, "life-" + sku, Duration.ofMinutes(15));
        assertThat(first.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(balance("reserved")).isEqualTo(3);

        // Replay returns the same reservation; a different payload under the same key conflicts.
        assertThat(
                        reservations
                                .reserve(sku, 3, reference, "life-" + sku, Duration.ofMinutes(15))
                                .id())
                .isEqualTo(first.id());
        assertThatThrownBy(
                        () ->
                                reservations.reserve(
                                        sku, 4, reference, "life-" + sku, Duration.ofMinutes(15)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(balance("reserved")).isEqualTo(3);

        assertThat(reservations.commit(first.id()).status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(reservations.commit(first.id()).status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(balance("on_hand")).isEqualTo(7);
        assertThat(balance("reserved")).isZero();
        assertThatThrownBy(() -> reservations.release(first.id()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.errorCode())
                                        .isEqualTo(ErrorCode.INVALID_RESERVATION_STATE));

        StockReservation second =
                reservations.reserve(
                        sku, 2, UUID.randomUUID(), "life2-" + sku, Duration.ofMinutes(15));
        assertThat(reservations.release(second.id()).status())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservations.release(second.id()).status())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThatThrownBy(() -> reservations.commit(second.id()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.errorCode())
                                        .isEqualTo(ErrorCode.INVALID_RESERVATION_STATE));
        assertThat(balance("on_hand")).isEqualTo(7);
        assertThat(balance("reserved")).isZero();
        assertThatThrownBy(() -> reservations.commit(UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND));
    }

    @Test
    void failedReservationLeavesNoRowsBehind() {
        stock(2);
        assertThatThrownBy(
                        () ->
                                reservations.reserve(
                                        sku,
                                        3,
                                        UUID.randomUUID(),
                                        "too-many-" + sku,
                                        Duration.ofMinutes(15)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
        assertThat(count("inventory.stock_reservation")).isZero();
        assertThat(count("inventory.stock_movement")).isEqualTo(1); // opening receipt only
        // Unknown SKU and invalid inputs are rejected up front.
        assertThatThrownBy(
                        () ->
                                reservations.reserve(
                                        UUID.randomUUID(),
                                        1,
                                        UUID.randomUUID(),
                                        "ghost",
                                        Duration.ofMinutes(1)))
                .isInstanceOf(BusinessException.class);
        for (long bad : new long[] {0, -1, 10_001}) {
            assertThatThrownBy(
                            () ->
                                    reservations.reserve(
                                            sku,
                                            bad,
                                            UUID.randomUUID(),
                                            "bad-" + bad,
                                            Duration.ofMinutes(1)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.errorCode())
                                            .isEqualTo(ErrorCode.INVALID_STOCK_INPUT));
        }
    }

    @Test
    void expiredHoldsAreReturnedExactlyOnce() throws Exception {
        stock(10);
        StockReservation due =
                reservations.reserve(
                        sku, 4, UUID.randomUUID(), "expire-" + sku, Duration.ofMinutes(15));
        StockReservation later =
                reservations.reserve(
                        sku, 1, UUID.randomUUID(), "later-" + sku, Duration.ofMinutes(15));
        jdbc.sql(
                        "UPDATE inventory.stock_reservation SET created_at = now() - interval '10 seconds', expires_at = now() - interval '5 seconds' WHERE id = :id")
                .param("id", due.id())
                .update();
        List<Callable<Object>> sweeps = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sweeps.add(() -> reservations.expireDue(50));
        }
        int expired = runTogether(sweeps).stream().mapToInt(r -> (Integer) r).sum();
        assertThat(expired).isEqualTo(1);
        assertThat(balance("reserved")).isEqualTo(1);
        assertThat(status(due.id())).isEqualTo("EXPIRED");
        assertThat(status(later.id())).isEqualTo("HELD");
        assertThat(reservations.expireDue(50)).isZero();
        assertThat(reservations.release(due.id()).status()).isEqualTo(ReservationStatus.EXPIRED);
    }

    private void stock(long onHand) {
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand) VALUES (:k,:w,:s,:q)")
                .param("k", sku)
                .param("w", warehouse)
                .param("s", shop)
                .param("q", onHand)
                .update();
        jdbc.sql(
                        "INSERT INTO inventory.stock_movement(id,sku_id,warehouse_id,movement_type,delta_on_hand,idempotency_key,reason) VALUES (gen_random_uuid(),:k,:w,'RECEIPT',:q,:key,'Opening stock for test')")
                .param("k", sku)
                .param("w", warehouse)
                .param("q", onHand)
                .param("key", "open-" + sku)
                .update();
    }

    private long balance(String column) {
        return jdbc.sql("SELECT " + column + " FROM inventory.stock_balance WHERE sku_id = :k")
                .param("k", sku)
                .query(Long.class)
                .single();
    }

    private String status(UUID id) {
        return jdbc.sql("SELECT status FROM inventory.stock_reservation WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private long count(String tableAndFilter) {
        String filter = tableAndFilter.contains(" WHERE ") ? " AND" : " WHERE";
        return jdbc.sql("SELECT count(*) FROM " + tableAndFilter + filter + " sku_id = :k")
                .param("k", sku)
                .query(Long.class)
                .single();
    }

    private static List<Object> runTogether(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return task.call();
                                }));
            }
            ready.await();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
