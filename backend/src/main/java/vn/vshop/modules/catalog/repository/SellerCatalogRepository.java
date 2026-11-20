package vn.vshop.modules.catalog.repository;

import java.util.Optional;
import java.util.UUID;
import vn.vshop.common.model.SearchResult;
import vn.vshop.modules.catalog.entity.OptionValue;
import vn.vshop.modules.catalog.entity.Product;
import vn.vshop.modules.catalog.entity.ProductOption;
import vn.vshop.modules.catalog.entity.ProductSku;
import vn.vshop.modules.catalog.entity.ProductStatus;
import vn.vshop.modules.catalog.entity.SkuOptionValue;
import vn.vshop.modules.catalog.entity.SkuStatus;
import vn.vshop.modules.catalog.model.ProductContentChange;
import vn.vshop.modules.catalog.model.SellerProduct;

/** Write side of the catalog. Every method is scoped by shop so IDs alone never cross tenants. */
public interface SellerCatalogRepository {
    boolean categoryExists(UUID categoryId);

    void insertProduct(Product product);

    void insertOption(ProductOption option);

    void insertOptionValue(OptionValue value);

    /** Returns false when the seller SKU or variant signature already exists. */
    boolean insertSku(ProductSku sku);

    void insertSkuOptionValue(SkuOptionValue selection);

    Optional<Product> findProductForUpdate(UUID productId, UUID shopId);

    Optional<SellerProduct> findProduct(UUID productId, UUID shopId);

    SearchResult<SellerProduct> listProducts(UUID shopId, int page, int size);

    /** Returns false when the expected version no longer matches. */
    boolean updateContent(UUID productId, UUID shopId, ProductContentChange change);

    boolean updateStatus(UUID productId, UUID shopId, long expectedVersion, ProductStatus status);

    Optional<ProductSku> findShopSku(UUID skuId, UUID shopId);

    Optional<ProductSku> findSkuForUpdate(UUID skuId, UUID productId);

    boolean updateSku(
            UUID skuId, UUID productId, long expectedVersion, long price, SkuStatus status);

    long countActiveSkus(UUID productId);

    long countSkus(UUID productId);
}
