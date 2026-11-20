package vn.vshop.modules.catalog.repository;

import java.util.List;
import java.util.Optional;
import vn.vshop.common.model.SearchResult;
import vn.vshop.modules.catalog.model.CategorySummary;
import vn.vshop.modules.catalog.model.ProductSearchCriteria;
import vn.vshop.modules.catalog.model.ProductSummary;
import vn.vshop.modules.catalog.model.PublicSku;
import vn.vshop.modules.catalog.model.SkuOffer;

public interface CatalogRepository {
    List<CategorySummary> findCategories();

    SearchResult<ProductSummary> search(ProductSearchCriteria criteria);

    Optional<ProductSummary> findVisibleBySlug(String slug);

    List<PublicSku> findActiveSkus(String slug);

    List<SkuOffer> findOffers(java.util.Collection<java.util.UUID> skuIds);
}
