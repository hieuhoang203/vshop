package vn.vshop.modules.catalog.repository.jdbc;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.common.model.SearchResult;
import vn.vshop.modules.catalog.model.CategorySummary;
import vn.vshop.modules.catalog.model.ProductSearchCriteria;
import vn.vshop.modules.catalog.model.ProductSummary;
import vn.vshop.modules.catalog.model.PublicSku;
import vn.vshop.modules.catalog.model.SkuOffer;
import vn.vshop.modules.catalog.repository.CatalogRepository;

@Repository
public class JdbcCatalogRepository implements CatalogRepository {
    private static final String FROM_VISIBLE_PRODUCT =
            """
         FROM catalog.product p
         JOIN catalog.category c ON c.id = p.category_id
         JOIN merchant.shop s ON s.id = p.shop_id
         WHERE p.status = 'ACTIVE' AND s.status = 'APPROVED'
             AND EXISTS (SELECT 1 FROM catalog.product_sku sku WHERE sku.product_id = p.id AND sku.status = 'ACTIVE')
        """;
    private static final String SELECT_SUMMARY =
            """
        SELECT p.id, p.slug, p.name, p.description, c.slug AS category_slug,
            c.name AS category_name, s.id AS shop_id, s.name AS shop_name,
            (SELECT min(sku.price) FROM catalog.product_sku sku
                WHERE sku.product_id = p.id AND sku.status = 'ACTIVE') AS price,
            'VND' AS currency,
            (SELECT coalesce(sum(b.on_hand - b.reserved - b.blocked), 0)
                FROM inventory.stock_balance b
                JOIN catalog.product_sku sku ON sku.id = b.sku_id
                JOIN inventory.warehouse w ON w.id = b.warehouse_id
                WHERE sku.product_id = p.id AND sku.status = 'ACTIVE' AND w.status = 'ACTIVE') AS stock,
            p.image_key
        """;
    private static final String FILTER =
            """
         AND (:query = '' OR lower(p.name || ' ' || p.description) LIKE :term ESCAPE '!')
         AND (:category = '' OR c.slug = :category)
         AND (SELECT min(sku.price) FROM catalog.product_sku sku
             WHERE sku.product_id = p.id AND sku.status = 'ACTIVE') BETWEEN :minPrice AND :maxPrice
        """;
    private final JdbcClient jdbc;

    public JdbcCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CategorySummary> findCategories() {
        return jdbc.sql(
                        """
            SELECT c.id, c.name, c.slug, count(p.id) AS count
            FROM catalog.category c
            LEFT JOIN catalog.product p ON p.category_id = c.id AND p.status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM merchant.shop s WHERE s.id = p.shop_id AND s.status = 'APPROVED')
                AND EXISTS (SELECT 1 FROM catalog.product_sku sku WHERE sku.product_id = p.id AND sku.status = 'ACTIVE')
            GROUP BY c.id ORDER BY c.sort_order, c.name
            """)
                .query(CategorySummary.class)
                .list();
    }

    @Override
    public SearchResult<ProductSummary> search(ProductSearchCriteria criteria) {
        String query = criteria.query().strip();
        String escaped =
                query.toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_");
        Map<String, Object> params =
                Map.of(
                        "query",
                        query,
                        "term",
                        "%" + escaped + "%",
                        "category",
                        criteria.category(),
                        "minPrice",
                        criteria.minPrice(),
                        "maxPrice",
                        criteria.maxPrice());
        long total =
                jdbc.sql("SELECT count(*)" + FROM_VISIBLE_PRODUCT + FILTER)
                        .params(params)
                        .query(Long.class)
                        .single();
        String orderBy =
                switch (criteria.sort()) {
                    case PRICE_ASC -> " ORDER BY price ASC, p.id ";
                    case PRICE_DESC -> " ORDER BY price DESC, p.id ";
                    case NEWEST -> " ORDER BY p.created_at DESC, p.id ";
                };
        List<ProductSummary> items =
                jdbc.sql(
                                SELECT_SUMMARY
                                        + FROM_VISIBLE_PRODUCT
                                        + FILTER
                                        + orderBy
                                        + """
             LIMIT :size OFFSET :offset
            """)
                        .params(params)
                        .param("size", criteria.size())
                        .param("offset", (long) criteria.page() * criteria.size())
                        .query(ProductSummary.class)
                        .list();
        return new SearchResult<>(items, total, criteria.page(), criteria.size());
    }

    @Override
    public Optional<ProductSummary> findVisibleBySlug(String slug) {
        return jdbc.sql(SELECT_SUMMARY + FROM_VISIBLE_PRODUCT + " AND p.slug = :slug")
                .param("slug", slug)
                .query(ProductSummary.class)
                .optional();
    }

    @Override
    public List<PublicSku> findActiveSkus(String slug) {
        return jdbc.sql(
                        """
            SELECT k.id,
                   (SELECT string_agg(v.label, ' / ' ORDER BY o.sort_order, o.code)
                      FROM catalog.sku_option_value sv
                      JOIN catalog.product_option o ON o.id = sv.option_id
                      JOIN catalog.option_value v ON v.id = sv.option_value_id
                     WHERE sv.sku_id = k.id) AS variant_label,
                   k.price,
                   (SELECT coalesce(sum(b.on_hand - b.reserved - b.blocked), 0)
                      FROM inventory.stock_balance b
                      JOIN inventory.warehouse w ON w.id = b.warehouse_id AND w.status = 'ACTIVE'
                     WHERE b.sku_id = k.id) AS available
            FROM catalog.product p
            JOIN merchant.shop s ON s.id = p.shop_id AND s.status = 'APPROVED'
            JOIN catalog.product_sku k ON k.product_id = p.id AND k.status = 'ACTIVE'
            WHERE p.slug = :slug AND p.status = 'ACTIVE'
            ORDER BY k.price, k.id
            """)
                .param("slug", slug)
                .query(PublicSku.class)
                .list();
    }

    @Override
    public List<SkuOffer> findOffers(java.util.Collection<java.util.UUID> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(
                        """
            SELECT k.id AS sku_id, p.id AS product_id, p.slug AS product_slug, p.name AS product_name,
                   p.image_key, k.seller_sku, k.price, s.id AS shop_id, s.name AS shop_name,
                   (SELECT string_agg(v.label, ' / ' ORDER BY o.sort_order, o.code)
                      FROM catalog.sku_option_value sv
                      JOIN catalog.product_option o ON o.id = sv.option_id
                      JOIN catalog.option_value v ON v.id = sv.option_value_id
                     WHERE sv.sku_id = k.id) AS variant_label,
                   (k.status = 'ACTIVE' AND p.status = 'ACTIVE' AND s.status = 'APPROVED') AS saleable
            FROM catalog.product_sku k
            JOIN catalog.product p ON p.id = k.product_id
            JOIN merchant.shop s ON s.id = k.shop_id
            WHERE k.id IN (:ids)
            """)
                .param("ids", skuIds)
                .query(SkuOffer.class)
                .list();
    }
}
