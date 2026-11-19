package vn.vshop.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vshop.modules.catalog.service.CatalogService;

@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=true",
            "vshop.worker.poll-ms=86400000"
        })
@AutoConfigureMockMvc
@Transactional
class DatabaseConstraintTests {
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CatalogService catalog;
    @MockitoBean JwtDecoder jwtDecoder;

    private record Item(UUID shopId, UUID productId, UUID skuId, String slug) {}

    private UUID shop() {
        UUID account = UUID.randomUUID();
        UUID shop = UUID.randomUUID();
        jdbc.sql("INSERT INTO identity.account(id,external_subject) VALUES (:id,:subject)")
                .param("id", account)
                .param("subject", "constraint-test-" + account)
                .update();
        jdbc.sql(
                        "INSERT INTO merchant.shop(id,owner_account_id,name,description,status) VALUES (:id,:owner,'Constraint Shop','Synthetic database constraint fixture.','APPROVED')")
                .param("id", shop)
                .param("owner", account)
                .update();
        return shop;
    }

    private Item item(UUID shop) {
        UUID category = UUID.randomUUID(), product = UUID.randomUUID(), sku = UUID.randomUUID();
        String slug = "constraint-product-" + product;
        jdbc.sql(
                        "INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Constraint Category',:slug)")
                .param("id", category)
                .param("slug", "constraint-" + category)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:category,:slug,'Constraint Product','Synthetic product fixture','lamp','ACTIVE')")
                .param("id", product)
                .param("shop", shop)
                .param("category", category)
                .param("slug", slug)
                .update();
        sku(sku, product, shop, "SKU-" + sku, "DEFAULT", 10000);
        return new Item(shop, product, sku, slug);
    }

    private void sku(UUID id, UUID product, UUID shop, String code, String signature, long price) {
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,variant_signature,price) VALUES (:id,:product,:shop,:code,:signature,:price)")
                .param("id", id)
                .param("product", product)
                .param("shop", shop)
                .param("code", code)
                .param("signature", signature)
                .param("price", price)
                .update();
    }

    private UUID warehouse(UUID shop) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:shop,:code,'Constraint Warehouse')")
                .param("id", id)
                .param("shop", shop)
                .param("code", "WH-" + id)
                .update();
        return id;
    }

    private void balance(
            UUID sku, UUID warehouse, UUID shop, long onHand, long reserved, long blocked) {
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand,reserved,blocked) VALUES (:sku,:warehouse,:shop,:onHand,:reserved,:blocked)")
                .param("sku", sku)
                .param("warehouse", warehouse)
                .param("shop", shop)
                .param("onHand", onHand)
                .param("reserved", reserved)
                .param("blocked", blocked)
                .update();
    }

    // Savepoint keeps the enclosing PostgreSQL test transaction usable after an expected violation.
    private void rejects(Runnable command) {
        TransactionTemplate nested = new TransactionTemplate(transactions);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        assertThatThrownBy(() -> nested.executeWithoutResult(status -> command.run()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orphanOwnerIsRejected() {
        rejects(
                () ->
                        jdbc.sql(
                                        "INSERT INTO merchant.shop(id,owner_account_id,name,description,status) VALUES (:id,:owner,'Orphan Shop','Must not reference a missing owner.','SUBMITTED')")
                                .param("id", UUID.randomUUID())
                                .param("owner", UUID.randomUUID())
                                .update());
    }

    @Test
    void pilotAllowsOnlyOneShopPerOwner() {
        UUID shop = shop();
        UUID owner =
                jdbc.sql("SELECT owner_account_id FROM merchant.shop WHERE id=:id")
                        .param("id", shop)
                        .query(UUID.class)
                        .single();
        rejects(
                () ->
                        jdbc.sql(
                                        "INSERT INTO merchant.shop(id,owner_account_id,name,description,status) VALUES (:id,:owner,'Duplicate Shop','Must not duplicate the pilot shop owner.','SUBMITTED')")
                                .param("id", UUID.randomUUID())
                                .param("owner", owner)
                                .update());
    }

    @Test
    void ownerMembershipMustMatchTheActualShopOwner() {
        UUID first = shop(), second = shop();
        UUID foreignOwner =
                jdbc.sql("SELECT owner_account_id FROM merchant.shop WHERE id=:id")
                        .param("id", second)
                        .query(UUID.class)
                        .single();
        rejects(
                () ->
                        jdbc.sql(
                                        "INSERT INTO merchant.shop_member(shop_id,account_id,role) VALUES (:shop,:account,'OWNER')")
                                .param("shop", first)
                                .param("account", foreignOwner)
                                .update());
    }

    @Test
    void skuCannotReferenceAProductOwnedByAnotherShop() {
        Item item = item(shop());
        UUID anotherShop = shop();
        rejects(
                () ->
                        sku(
                                UUID.randomUUID(),
                                item.productId(),
                                anotherShop,
                                "WRONG-SHOP",
                                "RED",
                                10000));
    }

    @Test
    void stockCannotUseAnotherShopWarehouse() {
        Item item = item(shop());
        UUID foreignWarehouse = warehouse(shop());
        rejects(() -> balance(item.skuId(), foreignWarehouse, item.shopId(), 10, 0, 0));
    }

    @Test
    void stockCannotBeNegativeOrOverAllocated() {
        Item item = item(shop());
        UUID warehouse = warehouse(item.shopId());
        rejects(() -> balance(item.skuId(), warehouse, item.shopId(), -1, 0, 0));
        rejects(() -> balance(item.skuId(), warehouse, item.shopId(), 10, 8, 3));
        rejects(() -> balance(item.skuId(), warehouse, item.shopId(), 10, -1, 0));
        balance(item.skuId(), warehouse, item.shopId(), 10, 2, 1);
    }

    @Test
    void sellerSkuAndVariantSignatureAreUniqueWithinTheirScopes() {
        Item item = item(shop());
        rejects(
                () ->
                        sku(
                                UUID.randomUUID(),
                                item.productId(),
                                item.shopId(),
                                "SKU-" + item.skuId(),
                                "SECOND",
                                10000));
        rejects(
                () ->
                        sku(
                                UUID.randomUUID(),
                                item.productId(),
                                item.shopId(),
                                "ANOTHER-CODE",
                                "DEFAULT",
                                10000));
    }

    @Test
    void moneyHasAnIntegerPositiveVndContract() {
        Item item = item(shop());
        rejects(
                () ->
                        jdbc.sql("UPDATE catalog.product_sku SET price=-1 WHERE id=:id")
                                .param("id", item.skuId())
                                .update());
        rejects(
                () ->
                        jdbc.sql("UPDATE catalog.product_sku SET currency='USD' WHERE id=:id")
                                .param("id", item.skuId())
                                .update());
        rejects(
                () ->
                        jdbc.sql("UPDATE catalog.product_sku SET price=NULL WHERE id=:id")
                                .param("id", item.skuId())
                                .update());
    }

    @Test
    void variantValuesCannotLeakAcrossProducts() {
        Item first = item(shop()), second = item(shop());
        UUID option = UUID.randomUUID(), value = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO catalog.product_option(id,product_id,code,name) VALUES (:id,:product,'color','Màu')")
                .param("id", option)
                .param("product", second.productId())
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.option_value(id,option_id,code,label) VALUES (:id,:option,'red','Đỏ')")
                .param("id", value)
                .param("option", option)
                .update();
        rejects(
                () ->
                        jdbc.sql(
                                        "INSERT INTO catalog.sku_option_value(sku_id,product_id,option_id,option_value_id) VALUES (:sku,:product,:option,:value)")
                                .param("sku", first.skuId())
                                .param("product", first.productId())
                                .param("option", option)
                                .param("value", value)
                                .update());
    }

    @Test
    void reservationQuantityAndIdempotencyAreEnforced() {
        Item item = item(shop());
        UUID warehouse = warehouse(item.shopId());
        balance(item.skuId(), warehouse, item.shopId(), 5, 0, 0);
        String insert =
                "INSERT INTO inventory.stock_reservation(id,sku_id,warehouse_id,quantity,reference_id,idempotency_key,expires_at) VALUES (:id,:sku,:warehouse,:quantity,:reference,:key,now()+interval '15 minutes')";
        String key = "constraint-" + UUID.randomUUID();
        jdbc.sql(insert)
                .param("id", UUID.randomUUID())
                .param("sku", item.skuId())
                .param("warehouse", warehouse)
                .param("quantity", 1)
                .param("reference", UUID.randomUUID())
                .param("key", key)
                .update();
        rejects(
                () ->
                        jdbc.sql(insert)
                                .param("id", UUID.randomUUID())
                                .param("sku", item.skuId())
                                .param("warehouse", warehouse)
                                .param("quantity", 1)
                                .param("reference", UUID.randomUUID())
                                .param("key", key)
                                .update());
        rejects(
                () ->
                        jdbc.sql(insert)
                                .param("id", UUID.randomUUID())
                                .param("sku", item.skuId())
                                .param("warehouse", warehouse)
                                .param("quantity", 0)
                                .param("reference", UUID.randomUUID())
                                .param("key", key + "-zero")
                                .update());
    }

    @Test
    void catalogAggregatesMultipleSkusAndAvailableStockWithoutDuplicatingPrice() {
        Item item = item(shop());
        UUID first = warehouse(item.shopId()), second = warehouse(item.shopId());
        UUID anotherSku = UUID.randomUUID();
        sku(anotherSku, item.productId(), item.shopId(), "SKU-" + anotherSku, "SECOND", 15000);
        balance(item.skuId(), first, item.shopId(), 10, 2, 1);
        balance(item.skuId(), second, item.shopId(), 5, 0, 1);
        balance(anotherSku, first, item.shopId(), 9, 3, 0);
        var summary = catalog.detail(item.slug());
        assertThat(summary.price()).isEqualTo(10000);
        assertThat(summary.stock()).isEqualTo(17);
    }
}
