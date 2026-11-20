package vn.vshop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.vshop.modules.identity.model.AddressInput;
import vn.vshop.modules.identity.service.AddressService;

/** Real committed transactions: concurrent address changes must keep exactly one default. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.pii.keys.test-1=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "vshop.pii.active-key=test-1"
        })
class AddressConcurrencyTests {
    @Autowired JdbcClient jdbc;
    @Autowired AddressService addresses;
    @MockitoBean JwtDecoder jwtDecoder;

    private final String buyer = "concurrency-test-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.sql(
                        "DELETE FROM identity.address WHERE account_id IN (SELECT id FROM identity.account WHERE external_subject = :b)")
                .param("b", buyer)
                .update();
        jdbc.sql("DELETE FROM identity.account WHERE external_subject = :b")
                .param("b", buyer)
                .update();
    }

    @Test
    void concurrentCreatesKeepExactlyOneDefaultAndRespectTheLimit() throws Exception {
        int threads = 14; // more than the 10-address limit
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int created = 0;
        int refused = 0;
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int n = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    try {
                                        addresses.create(
                                                buyer,
                                                new AddressInput(
                                                        "N" + n,
                                                        "Nguyễn Văn A",
                                                        "0912345678",
                                                        "Số " + n + " phố Cổ Nhuế",
                                                        "Phường A",
                                                        "Hà Nội",
                                                        true));
                                        return true;
                                    } catch (vn.vshop.common.exception.BusinessException e) {
                                        return false;
                                    }
                                }));
            }
            ready.await();
            go.countDown();
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    created++;
                } else {
                    refused++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(created).isEqualTo(10);
        assertThat(refused).isEqualTo(4);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM identity.address a JOIN identity.account c ON c.id = a.account_id WHERE c.external_subject = :b AND a.is_default")
                                .param("b", buyer)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(addresses.list(buyer)).hasSize(10);
    }
}
