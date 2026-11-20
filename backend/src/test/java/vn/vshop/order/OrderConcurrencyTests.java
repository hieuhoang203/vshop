package vn.vshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import vn.vshop.modules.checkout.service.CartService;
import vn.vshop.modules.checkout.service.QuoteService;
import vn.vshop.modules.identity.model.AddressInput;
import vn.vshop.modules.identity.service.AddressService;
import vn.vshop.modules.order.entity.PaymentMethod;
import vn.vshop.modules.order.model.GroupDetail;
import vn.vshop.modules.order.service.OrderService;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.service.PolicyService;

/** Real committed transactions racing each other: the cases a rolled-back test cannot show. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false"
        })
class OrderConcurrencyTests {
    @Autowired JdbcClient jdbc;
    @Autowired CartService carts;
    @Autowired QuoteService quotes;
    @Autowired OrderService orders;
    @Autowired AddressService addresses;
    @Autowired PolicyService policies;
    @MockitoBean JwtDecoder jwtDecoder;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final String admin = "order-conc-admin-" + run;
    private UUID category;
    private final List<UUID> shops = new ArrayList<>();
    private final List<String> subjects = new ArrayList<>();

    @BeforeEach
    void seed() {
        category = UUID.randomUUID();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'OrderConc',:slug)")
                .param("id", category)
                .param("slug", "order-conc-" + category)
                .update();
        subjects.add(admin);
        for (var entry :
                java.util.Map.of(
                                PolicyType.SHIPPING_FLAT, java.util.Map.of("amount", 10_000L),
                                PolicyType.RETURN, java.util.Map.of("windowDays", 7L),
                                PolicyType.COD_LIMITS,
                                        java.util.Map.of(
                                                "maxOrderAmount",
                                                50_000_000L,
                                                "maxOpenOrders",
                                                50L),
                                PolicyType.PAYMENT_WINDOW,
                                        java.util.Map.of(
                                                "paymentMinutes",
                                                15L,
                                                "reservationBufferMinutes",
                                                5L))
                        .entrySet()) {
            var draft = policies.createDraft(admin, entry.getKey(), entry.getValue());
            policies.approve(admin, draft.id());
        }
    }

    @AfterEach
    void cleanUp() {
        String ids = "SELECT a.id FROM identity.account a WHERE a.external_subject LIKE :p";
        // Purges every run's leftovers (prefix), so a crashed run cannot pollute the shared test
        // DB.
        String p = "order-conc-%";
        jdbc.sql(
                        "DELETE FROM platform.outbox_event WHERE aggregate_type = 'order' AND aggregate_id IN (SELECT o.id FROM sales.seller_order o JOIN sales.checkout_group g ON g.id = o.checkout_group_id WHERE g.buyer_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql(
                        "DELETE FROM platform.outbox_event WHERE aggregate_id IN (SELECT id FROM platform.policy_version WHERE created_by_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql(
                        "DELETE FROM platform.audit_event WHERE resource_id IN (SELECT id FROM platform.policy_version WHERE created_by_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql(
                        "DELETE FROM sales.order_status_history WHERE seller_order_id IN (SELECT o.id FROM sales.seller_order o JOIN sales.checkout_group g ON g.id = o.checkout_group_id WHERE g.buyer_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql(
                        "DELETE FROM sales.order_line_reservation WHERE order_line_id IN (SELECT l.id FROM sales.order_line l JOIN sales.seller_order o ON o.id = l.seller_order_id JOIN sales.checkout_group g ON g.id = o.checkout_group_id WHERE g.buyer_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql(
                        "DELETE FROM sales.order_line WHERE seller_order_id IN (SELECT o.id FROM sales.seller_order o JOIN sales.checkout_group g ON g.id = o.checkout_group_id WHERE g.buyer_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql(
                        "DELETE FROM sales.seller_order WHERE checkout_group_id IN (SELECT g.id FROM sales.checkout_group g WHERE g.buyer_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql("DELETE FROM sales.checkout_group WHERE buyer_account_id IN (" + ids + ")")
                .param("p", p)
                .update();
        jdbc.sql("DELETE FROM checkout.quote WHERE buyer_account_id IN (" + ids + ")")
                .param("p", p)
                .update();
        jdbc.sql(
                        "DELETE FROM checkout.cart_line WHERE cart_id IN (SELECT c.id FROM checkout.cart c WHERE c.buyer_account_id IN ("
                                + ids
                                + "))")
                .param("p", p)
                .update();
        jdbc.sql("DELETE FROM checkout.cart WHERE buyer_account_id IN (" + ids + ")")
                .param("p", p)
                .update();
        jdbc.sql("DELETE FROM identity.address WHERE account_id IN (" + ids + ")")
                .param("p", p)
                .update();
        String shopIds =
                "SELECT s.id FROM merchant.shop s JOIN identity.account a ON a.id = s.owner_account_id WHERE a.external_subject LIKE 'order-conc-owner-%'";
        jdbc.sql(
                        "DELETE FROM inventory.stock_movement WHERE sku_id IN (SELECT id FROM catalog.product_sku WHERE shop_id IN ("
                                + shopIds
                                + "))")
                .update();
        jdbc.sql(
                        "DELETE FROM inventory.stock_reservation WHERE sku_id IN (SELECT id FROM catalog.product_sku WHERE shop_id IN ("
                                + shopIds
                                + "))")
                .update();
        jdbc.sql("DELETE FROM inventory.stock_balance WHERE shop_id IN (" + shopIds + ")").update();
        jdbc.sql("DELETE FROM inventory.warehouse WHERE shop_id IN (" + shopIds + ")").update();
        jdbc.sql("DELETE FROM catalog.product_sku WHERE shop_id IN (" + shopIds + ")").update();
        jdbc.sql("DELETE FROM catalog.product WHERE shop_id IN (" + shopIds + ")").update();
        jdbc.sql("DELETE FROM merchant.shop WHERE id IN (" + shopIds + ")").update();
        jdbc.sql("DELETE FROM catalog.category WHERE slug LIKE 'order-conc-%'").update();
        jdbc.sql("DELETE FROM platform.policy_version WHERE created_by_account_id IN (" + ids + ")")
                .param("p", p)
                .update();
        jdbc.sql("DELETE FROM identity.account WHERE external_subject LIKE :p")
                .param("p", p)
                .update();
    }

    @Test
    void eightParallelRetriesOfOneRequestCreateExactlyOneOrder() throws Exception {
        UUID sku = sku("one", 50_000, 10);
        String buyer = buyer("retry");
        UUID address = address(buyer);
        carts.addLine(buyer, sku, 2);
        UUID quote = quotes.create(buyer, address).id();

        List<Callable<GroupDetail>> tries = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tries.add(() -> orders.place(buyer, quote, PaymentMethod.COD, "parallel-key-" + run));
        }
        List<GroupDetail> results = race(tries);

        assertThat(results).hasSize(8);
        assertThat(results.stream().map(r -> r.group().id()).distinct()).hasSize(1);
        assertThat(results.stream().filter(r -> !r.replayed())).hasSize(1);
        assertThat(count("sales.checkout_group", buyer)).isEqualTo(1);
        assertThat(onHand(sku)).isEqualTo(8);
        assertThat(reserved(sku)).isZero();
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM platform.outbox_event WHERE event_type='OrderPlaced' AND aggregate_id IN (SELECT o.id FROM sales.seller_order o JOIN sales.checkout_group g ON g.id=o.checkout_group_id JOIN identity.account a ON a.id=g.buyer_account_id WHERE a.external_subject=:b)")
                                .param("b", buyer)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void theLastUnitGoesToExactlyOneOfTwoBuyers() throws Exception {
        UUID sku = sku("last", 50_000, 1);
        String first = buyer("first");
        String second = buyer("second");
        UUID quoteA = prepare(first, sku);
        UUID quoteB = prepare(second, sku);

        List<Callable<GroupDetail>> tries =
                List.of(
                        () -> orders.place(first, quoteA, PaymentMethod.COD, "last-a-" + run),
                        () -> orders.place(second, quoteB, PaymentMethod.COD, "last-b-" + run));
        int ok = 0;
        List<String> failures = new ArrayList<>();
        for (Future<GroupDetail> f : submit(tries)) {
            try {
                f.get();
                ok++;
            } catch (ExecutionException e) {
                failures.add(((BusinessException) e.getCause()).code());
            }
        }
        assertThat(ok).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isIn("INSUFFICIENT_STOCK", "SKU_UNAVAILABLE");
        assertThat(onHand(sku)).isZero();
        assertThat(reserved(sku)).isZero();
        assertThat(count("sales.checkout_group", first) + count("sales.checkout_group", second))
                .isEqualTo(1);
        // The loser rolled back completely: its cart is untouched and nothing is left reserved.
        String loser = count("sales.checkout_group", first) == 0 ? first : second;
        assertThat(count("sales.seller_order", loser)).isZero();
        assertThat(
                        jdbc.sql(
                                        "SELECT COALESCE(SUM(l.quantity),0) FROM checkout.cart_line l JOIN checkout.cart c ON c.id=l.cart_id JOIN identity.account a ON a.id=c.buyer_account_id WHERE a.external_subject=:b")
                                .param("b", loser)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void checkoutsThatBuyTheSamePairOfSkusInOppositeOrderNeverDeadlock() throws Exception {
        UUID x = sku("x", 20_000, 100);
        UUID y = sku("y", 30_000, 100);
        String one = buyer("one");
        String two = buyer("two");
        UUID addressOne = address(one);
        UUID addressTwo = address(two);
        for (int round = 0; round < 6; round++) {
            carts.addLine(one, x, 1);
            carts.addLine(one, y, 1);
            carts.addLine(two, y, 1);
            carts.addLine(two, x, 1);
            UUID quoteOne = quotes.create(one, addressOne).id();
            UUID quoteTwo = quotes.create(two, addressTwo).id();
            int r = round;
            List<GroupDetail> results =
                    race(
                            List.of(
                                    () ->
                                            orders.place(
                                                    one,
                                                    quoteOne,
                                                    PaymentMethod.COD,
                                                    "opp-one-" + r + "-" + run),
                                    () ->
                                            orders.place(
                                                    two,
                                                    quoteTwo,
                                                    PaymentMethod.COD,
                                                    "opp-two-" + r + "-" + run)));
            assertThat(results).hasSize(2);
        }
        assertThat(onHand(x)).isEqualTo(100 - 12);
        assertThat(onHand(y)).isEqualTo(100 - 12);
    }

    // ------------------------------------------------------------------ helpers

    private <T> List<T> race(List<Callable<T>> tasks) throws Exception {
        List<T> results = new ArrayList<>();
        for (Future<T> f : submit(tasks)) {
            results.add(f.get());
        }
        return results;
    }

    private <T> List<Future<T>> submit(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
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
            // Wait for all before shutting the pool down.
            for (Future<T> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException ignored) {
                    // the caller inspects each future's outcome
                }
            }
            return futures;
        } finally {
            pool.shutdown();
        }
    }

    private UUID prepare(String buyer, UUID sku) {
        UUID address = address(buyer);
        carts.addLine(buyer, sku, 1);
        return quotes.create(buyer, address).id();
    }

    private String buyer(String name) {
        String subject = "order-conc-" + name + "-" + run;
        subjects.add(subject);
        return subject;
    }

    private UUID address(String subject) {
        return addresses
                .create(
                        subject,
                        new AddressInput(
                                null,
                                "Nguyen Van A",
                                "0912345678",
                                "12 Nguyen Hue",
                                "Ben Nghe",
                                "Ho Chi Minh",
                                true))
                .id();
    }

    /** One shop per SKU so checkout produces one order per SKU. */
    private UUID sku(String code, long price, long stock) {
        UUID shop = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        String owner = "order-conc-owner-" + code + "-" + run;
        jdbc.sql("INSERT INTO identity.account(external_subject) VALUES (:s)")
                .param("s", owner)
                .update();
        jdbc.sql(
                        "INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,id,:name,'Order concurrency test shop','APPROVED' FROM identity.account WHERE external_subject=:s")
                .param("id", shop)
                .param("name", "Conc " + code)
                .param("s", owner)
                .update();
        shops.add(shop);
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,:name,'Concurrency test product','lamp','ACTIVE')")
                .param("id", id)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "order-conc-" + id)
                .param("name", "Conc product " + code)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,:price)")
                .param("id", id)
                .param("shop", shop)
                .param("code", code + "-" + id)
                .param("price", price)
                .update();
        UUID warehouse = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:shop,'W1','Kho')")
                .param("id", warehouse)
                .param("shop", shop)
                .update();
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand) VALUES (:sku,:wh,:shop,:q)")
                .param("sku", id)
                .param("wh", warehouse)
                .param("shop", shop)
                .param("q", stock)
                .update();
        return id;
    }

    private long count(String table, String buyer) {
        String sql =
                table.equals("sales.checkout_group")
                        ? "SELECT count(*) FROM sales.checkout_group g JOIN identity.account a ON a.id=g.buyer_account_id WHERE a.external_subject=:b"
                        : "SELECT count(*) FROM sales.seller_order o JOIN sales.checkout_group g ON g.id=o.checkout_group_id JOIN identity.account a ON a.id=g.buyer_account_id WHERE a.external_subject=:b";
        return jdbc.sql(sql).param("b", buyer).query(Long.class).single();
    }

    private long onHand(UUID sku) {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(on_hand),0) FROM inventory.stock_balance WHERE sku_id=:s")
                .param("s", sku)
                .query(Long.class)
                .single();
    }

    private long reserved(UUID sku) {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(reserved),0) FROM inventory.stock_balance WHERE sku_id=:s")
                .param("s", sku)
                .query(Long.class)
                .single();
    }
}
