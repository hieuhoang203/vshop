package vn.vshop.modules.catalog.repository.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.common.model.SearchResult;
import vn.vshop.modules.catalog.entity.OptionValue;
import vn.vshop.modules.catalog.entity.Product;
import vn.vshop.modules.catalog.entity.ProductOption;
import vn.vshop.modules.catalog.entity.ProductReview;
import vn.vshop.modules.catalog.entity.ProductSku;
import vn.vshop.modules.catalog.entity.ProductStatus;
import vn.vshop.modules.catalog.entity.SkuOptionValue;
import vn.vshop.modules.catalog.entity.SkuStatus;
import vn.vshop.modules.catalog.model.ProductContentChange;
import vn.vshop.modules.catalog.model.SellerProduct;
import vn.vshop.modules.catalog.repository.SellerCatalogRepository;

@Repository
public class JdbcSellerCatalogRepository implements SellerCatalogRepository {
    private static final String PRODUCT_COLUMNS =
            "id, shop_id, category_id, slug, name, description, image_key, status, rejection_reason, version, created_at, updated_at";
    private static final String SKU_COLUMNS =
            "id, product_id, shop_id, seller_sku, variant_signature, price, currency, status, version, created_at, updated_at";
    private final JdbcClient jdbc;

    public JdbcSellerCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean categoryExists(UUID categoryId) {
        return jdbc.sql("SELECT count(*) FROM catalog.category WHERE id = :id")
                        .param("id", categoryId)
                        .query(Long.class)
                        .single()
                == 1;
    }

    @Override
    public void insertProduct(Product product) {
        jdbc.sql(
                        """
            INSERT INTO catalog.product (id, shop_id, category_id, slug, name, description, image_key, status)
            VALUES (:id, :shop, :category, :slug, :name, :description, :image, :status)
            """)
                .param("id", product.id())
                .param("shop", product.shopId())
                .param("category", product.categoryId())
                .param("slug", product.slug())
                .param("name", product.name())
                .param("description", product.description())
                .param("image", product.imageKey())
                .param("status", product.status().name())
                .update();
    }

    @Override
    public void insertOption(ProductOption option) {
        jdbc.sql(
                        """
            INSERT INTO catalog.product_option (id, product_id, code, name, sort_order)
            VALUES (:id, :product, :code, :name, :sort)
            """)
                .param("id", option.id())
                .param("product", option.productId())
                .param("code", option.code())
                .param("name", option.name())
                .param("sort", option.sortOrder())
                .update();
    }

    @Override
    public void insertOptionValue(OptionValue value) {
        jdbc.sql(
                        """
            INSERT INTO catalog.option_value (id, option_id, code, label, sort_order)
            VALUES (:id, :option, :code, :label, :sort)
            """)
                .param("id", value.id())
                .param("option", value.optionId())
                .param("code", value.code())
                .param("label", value.label())
                .param("sort", value.sortOrder())
                .update();
    }

    @Override
    public boolean insertSku(ProductSku sku) {
        return jdbc.sql(
                                """
            INSERT INTO catalog.product_sku (id, product_id, shop_id, seller_sku, variant_signature, price, currency, status)
            VALUES (:id, :product, :shop, :code, :signature, :price, :currency, :status)
            ON CONFLICT DO NOTHING
            """)
                        .param("id", sku.id())
                        .param("product", sku.productId())
                        .param("shop", sku.shopId())
                        .param("code", sku.sellerSku())
                        .param("signature", sku.variantSignature())
                        .param("price", sku.price())
                        .param("currency", sku.currency())
                        .param("status", sku.status().name())
                        .update()
                == 1;
    }

    @Override
    public void insertSkuOptionValue(SkuOptionValue selection) {
        jdbc.sql(
                        """
            INSERT INTO catalog.sku_option_value (sku_id, product_id, option_id, option_value_id)
            VALUES (:sku, :product, :option, :value)
            """)
                .param("sku", selection.skuId())
                .param("product", selection.productId())
                .param("option", selection.optionId())
                .param("value", selection.optionValueId())
                .update();
    }

    @Override
    public Optional<Product> findProductForUpdate(UUID productId, UUID shopId) {
        return jdbc.sql(
                        "SELECT "
                                + PRODUCT_COLUMNS
                                + " FROM catalog.product WHERE id = :id AND shop_id = :shop FOR UPDATE")
                .param("id", productId)
                .param("shop", shopId)
                .query(Product.class)
                .optional();
    }

    @Override
    public Optional<Product> lockProduct(UUID productId) {
        return jdbc.sql(
                        "SELECT "
                                + PRODUCT_COLUMNS
                                + " FROM catalog.product WHERE id = :id FOR UPDATE")
                .param("id", productId)
                .query(Product.class)
                .optional();
    }

    @Override
    public Optional<SellerProduct> findProduct(UUID productId, UUID shopId) {
        List<Product> products =
                jdbc.sql(
                                "SELECT "
                                        + PRODUCT_COLUMNS
                                        + " FROM catalog.product WHERE id = :id AND shop_id = :shop")
                        .param("id", productId)
                        .param("shop", shopId)
                        .query(Product.class)
                        .list();
        return assemble(products).stream().findFirst();
    }

    @Override
    public SearchResult<SellerProduct> listProducts(UUID shopId, int page, int size) {
        long total =
                jdbc.sql("SELECT count(*) FROM catalog.product WHERE shop_id = :shop")
                        .param("shop", shopId)
                        .query(Long.class)
                        .single();
        List<Product> products =
                jdbc.sql(
                                "SELECT "
                                        + PRODUCT_COLUMNS
                                        + """
             FROM catalog.product WHERE shop_id = :shop
             ORDER BY created_at DESC, id LIMIT :size OFFSET :offset
            """)
                        .param("shop", shopId)
                        .param("size", size)
                        .param("offset", (long) page * size)
                        .query(Product.class)
                        .list();
        return new SearchResult<>(assemble(products), total, page, size);
    }

    @Override
    public boolean updateContent(UUID productId, UUID shopId, ProductContentChange change) {
        return jdbc.sql(
                                """
            UPDATE catalog.product SET name = :name, description = :description,
                category_id = :category, image_key = :image,
                status = CASE WHEN status IN ('ACTIVE', 'HIDDEN') THEN 'PENDING_REVIEW' ELSE status END,
                updated_at = now(), version = version + 1
            WHERE id = :id AND shop_id = :shop AND version = :version
            """)
                        .param("id", productId)
                        .param("shop", shopId)
                        .param("name", change.name())
                        .param("description", change.description())
                        .param("category", change.categoryId())
                        .param("image", change.imageKey())
                        .param("version", change.expectedVersion())
                        .update()
                == 1;
    }

    @Override
    public boolean updateStatus(
            UUID productId,
            UUID shopId,
            long expectedVersion,
            ProductStatus status,
            String rejectionReason) {
        return jdbc.sql(
                                """
            UPDATE catalog.product SET status = :status, rejection_reason = :reason,
                updated_at = now(), version = version + 1
            WHERE id = :id AND shop_id = :shop AND version = :version
            """)
                        .param("id", productId)
                        .param("shop", shopId)
                        .param("status", status.name())
                        .param("reason", rejectionReason, java.sql.Types.VARCHAR)
                        .param("version", expectedVersion)
                        .update()
                == 1;
    }

    @Override
    public SearchResult<SellerProduct> listProductsByStatus(
            ProductStatus status, int page, int size) {
        long total =
                jdbc.sql("SELECT count(*) FROM catalog.product WHERE status = :status")
                        .param("status", status.name())
                        .query(Long.class)
                        .single();
        List<Product> products =
                jdbc.sql(
                                "SELECT "
                                        + PRODUCT_COLUMNS
                                        + """
             FROM catalog.product WHERE status = :status
             ORDER BY created_at, id LIMIT :size OFFSET :offset
            """)
                        .param("status", status.name())
                        .param("size", size)
                        .param("offset", (long) page * size)
                        .query(Product.class)
                        .list();
        return new SearchResult<>(assemble(products), total, page, size);
    }

    @Override
    public void insertReview(ProductReview review) {
        jdbc.sql(
                        """
            INSERT INTO catalog.product_review
                (id, product_id, shop_id, reviewer_account_id, decision, reason, product_version)
            VALUES (:id, :product, :shop, :reviewer, :decision, :reason, :version)
            """)
                .param("id", review.id())
                .param("product", review.productId())
                .param("shop", review.shopId())
                .param("reviewer", review.reviewerAccountId())
                .param("decision", review.decision().name())
                .param("reason", review.reason())
                .param("version", review.productVersion())
                .update();
    }

    @Override
    public Optional<ProductSku> findShopSku(UUID skuId, UUID shopId) {
        return jdbc.sql(
                        "SELECT "
                                + SKU_COLUMNS
                                + " FROM catalog.product_sku WHERE id = :id AND shop_id = :shop")
                .param("id", skuId)
                .param("shop", shopId)
                .query(ProductSku.class)
                .optional();
    }

    @Override
    public Optional<ProductSku> findSkuForUpdate(UUID skuId, UUID productId) {
        return jdbc.sql(
                        "SELECT "
                                + SKU_COLUMNS
                                + " FROM catalog.product_sku WHERE id = :id AND product_id = :product FOR UPDATE")
                .param("id", skuId)
                .param("product", productId)
                .query(ProductSku.class)
                .optional();
    }

    @Override
    public boolean updateSku(
            UUID skuId, UUID productId, long expectedVersion, long price, SkuStatus status) {
        return jdbc.sql(
                                """
            UPDATE catalog.product_sku SET price = :price, status = :status,
                updated_at = now(), version = version + 1
            WHERE id = :id AND product_id = :product AND version = :version
            """)
                        .param("id", skuId)
                        .param("product", productId)
                        .param("price", price)
                        .param("status", status.name())
                        .param("version", expectedVersion)
                        .update()
                == 1;
    }

    @Override
    public long countActiveSkus(UUID productId) {
        return jdbc.sql(
                        "SELECT count(*) FROM catalog.product_sku WHERE product_id = :id AND status = 'ACTIVE'")
                .param("id", productId)
                .query(Long.class)
                .single();
    }

    @Override
    public long countSkus(UUID productId) {
        return jdbc.sql("SELECT count(*) FROM catalog.product_sku WHERE product_id = :id")
                .param("id", productId)
                .query(Long.class)
                .single();
    }

    private List<SellerProduct> assemble(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = products.stream().map(Product::id).toList();
        Map<UUID, List<OptionValue>> valuesByOption =
                jdbc
                        .sql(
                                """
            SELECT v.id, v.option_id, v.code, v.label, v.sort_order
            FROM catalog.option_value v JOIN catalog.product_option o ON o.id = v.option_id
            WHERE o.product_id IN (:ids) ORDER BY v.sort_order, v.code
            """)
                        .param("ids", ids)
                        .query(OptionValue.class)
                        .list()
                        .stream()
                        .collect(Collectors.groupingBy(OptionValue::optionId));
        Map<UUID, List<SellerProduct.Option>> optionsByProduct =
                jdbc
                        .sql(
                                "SELECT id, product_id, code, name, sort_order FROM catalog.product_option WHERE product_id IN (:ids) ORDER BY sort_order, code")
                        .param("ids", ids)
                        .query(ProductOption.class)
                        .list()
                        .stream()
                        .map(
                                option ->
                                        new SellerProduct.Option(
                                                option,
                                                valuesByOption.getOrDefault(
                                                        option.id(), List.of())))
                        .collect(Collectors.groupingBy(o -> o.option().productId()));
        Map<UUID, Map<String, String>> selections = new HashMap<>();
        jdbc.sql(
                        """
            SELECT sv.sku_id, o.code AS option_code, v.code AS value_code
            FROM catalog.sku_option_value sv
            JOIN catalog.product_option o ON o.id = sv.option_id
            JOIN catalog.option_value v ON v.id = sv.option_value_id
            WHERE sv.product_id IN (:ids)
            """)
                .param("ids", ids)
                .query(
                        (rs, row) -> {
                            selections
                                    .computeIfAbsent(
                                            rs.getObject("sku_id", UUID.class),
                                            key -> new LinkedHashMap<>())
                                    .put(rs.getString("option_code"), rs.getString("value_code"));
                            return row;
                        })
                .list();
        Map<UUID, String> shopNames = new HashMap<>();
        jdbc.sql("SELECT id, name FROM merchant.shop WHERE id IN (:ids)")
                .param("ids", products.stream().map(Product::shopId).distinct().toList())
                .query(
                        (rs, row) -> {
                            shopNames.put(rs.getObject("id", UUID.class), rs.getString("name"));
                            return row;
                        })
                .list();
        Map<UUID, Long> available = new HashMap<>();
        jdbc.sql(
                        """
            SELECT b.sku_id, coalesce(sum(b.on_hand - b.reserved - b.blocked), 0) AS available
            FROM inventory.stock_balance b
            JOIN inventory.warehouse w ON w.id = b.warehouse_id AND w.status = 'ACTIVE'
            JOIN catalog.product_sku k ON k.id = b.sku_id
            WHERE k.product_id IN (:ids) GROUP BY b.sku_id
            """)
                .param("ids", ids)
                .query(
                        (rs, row) -> {
                            available.put(
                                    rs.getObject("sku_id", UUID.class), rs.getLong("available"));
                            return row;
                        })
                .list();
        Map<UUID, List<SellerProduct.Sku>> skusByProduct =
                jdbc
                        .sql(
                                "SELECT "
                                        + SKU_COLUMNS
                                        + " FROM catalog.product_sku WHERE product_id IN (:ids) ORDER BY seller_sku, id")
                        .param("ids", ids)
                        .query(ProductSku.class)
                        .list()
                        .stream()
                        .map(
                                sku ->
                                        new SellerProduct.Sku(
                                                sku,
                                                selections.getOrDefault(sku.id(), Map.of()),
                                                available.getOrDefault(sku.id(), 0L)))
                        .collect(Collectors.groupingBy(s -> s.sku().productId()));
        List<SellerProduct> result = new ArrayList<>();
        for (Product product : products) {
            result.add(
                    new SellerProduct(
                            product,
                            shopNames.getOrDefault(product.shopId(), ""),
                            optionsByProduct.getOrDefault(product.id(), List.of()),
                            skusByProduct.getOrDefault(product.id(), List.of())));
        }
        return result;
    }
}
