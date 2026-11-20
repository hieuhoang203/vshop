package vn.vshop.modules.catalog.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.model.SearchResult;
import vn.vshop.modules.catalog.model.CategorySummary;
import vn.vshop.modules.catalog.model.ProductSearchCriteria;
import vn.vshop.modules.catalog.model.ProductSummary;
import vn.vshop.modules.catalog.model.PublicSku;
import vn.vshop.modules.catalog.model.SkuOffer;
import vn.vshop.modules.catalog.repository.CatalogRepository;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogRepository catalog;

    public CatalogService(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    public List<CategorySummary> categories() {
        return catalog.findCategories();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchResult<ProductSummary> search(ProductSearchCriteria criteria) {
        if (criteria.minPrice() > criteria.maxPrice()) {
            throw new BusinessException(
                    ErrorCode.INVALID_CATALOG_FILTER, "Khoảng giá chưa hợp lệ.");
        }
        return catalog.search(criteria);
    }

    public ProductSummary detail(String slug) {
        return catalog.findVisibleBySlug(slug)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.PRODUCT_NOT_FOUND,
                                        "Sản phẩm không còn hiển thị."));
    }

    /** Buyable SKUs of a visible product; 404 when the product is not visible. */
    public List<PublicSku> skus(String slug) {
        detail(slug);
        return catalog.findActiveSkus(slug);
    }

    /** Current price, variant and saleability for SKUs; unknown IDs are simply absent. */
    public List<SkuOffer> offers(java.util.Collection<java.util.UUID> skuIds) {
        return catalog.findOffers(skuIds);
    }
}
