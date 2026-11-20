package vn.vshop.modules.catalog.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.model.SearchResult;
import vn.vshop.modules.catalog.entity.Product;
import vn.vshop.modules.catalog.entity.ProductReview;
import vn.vshop.modules.catalog.entity.ProductReviewDecision;
import vn.vshop.modules.catalog.entity.ProductStatus;
import vn.vshop.modules.catalog.model.SellerProduct;
import vn.vshop.modules.catalog.repository.SellerCatalogRepository;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.platform.service.AuditEventService;

/**
 * Admin moderation of product content (flow F02). A decision is bound to the exact content version
 * the admin looked at, so a seller edit made after the queue was loaded cannot be approved by
 * mistake.
 */
@Service
@Transactional(readOnly = true)
public class ProductModerationService {
    private final SellerCatalogRepository catalog;
    private final AccountService accounts;
    private final AuditEventService events;

    public ProductModerationService(
            SellerCatalogRepository catalog, AccountService accounts, AuditEventService events) {
        this.catalog = catalog;
        this.accounts = accounts;
        this.events = events;
    }

    public SearchResult<SellerProduct> queue(int page, int size) {
        return catalog.listProductsByStatus(ProductStatus.PENDING_REVIEW, page, size);
    }

    @Transactional
    public SellerProduct review(
            String subject,
            UUID productId,
            ProductReviewDecision decision,
            String reason,
            long expectedVersion) {
        UUID reviewer = accounts.requireActiveAccount(subject);
        Product product =
                catalog.lockProduct(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PRODUCT_NOT_FOUND,
                                                "Không tìm thấy sản phẩm."));
        if (product.status() != ProductStatus.PENDING_REVIEW) {
            throw new BusinessException(
                    ErrorCode.INVALID_PRODUCT_STATE,
                    "Sản phẩm không ở trạng thái chờ duyệt. Vui lòng tải lại hàng đợi.");
        }
        if (product.version() != expectedVersion) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT,
                    "Người bán đã thay đổi sản phẩm sau khi bạn mở. Vui lòng xem lại bản mới.");
        }
        String stripped = reason.strip();
        if (stripped.length() < 5) {
            throw new BusinessException(
                    ErrorCode.INVALID_PRODUCT_INPUT, "Cần nêu lý do ít nhất 5 ký tự.");
        }
        boolean approved = decision == ProductReviewDecision.APPROVED;
        if (approved && catalog.countActiveSkus(productId) == 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_PRODUCT_STATE,
                    "Sản phẩm không có SKU đang bán nên chưa thể duyệt.");
        }
        ProductStatus target = approved ? ProductStatus.ACTIVE : ProductStatus.REJECTED;
        if (!catalog.updateStatus(
                productId, product.shopId(), expectedVersion, target, approved ? null : stripped)) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT, "Sản phẩm vừa được thay đổi. Vui lòng thử lại.");
        }
        catalog.insertReview(
                new ProductReview(
                        UUID.randomUUID(),
                        productId,
                        product.shopId(),
                        reviewer,
                        decision,
                        stripped,
                        expectedVersion,
                        OffsetDateTime.now(ZoneOffset.UTC)));
        events.record(
                subject,
                approved ? "ProductApproved" : "ProductRejected",
                product.shopId(),
                "product "
                        + productId
                        + ": "
                        + (stripped.length() > 200 ? stripped.substring(0, 200) : stripped));
        return catalog.findProduct(productId, product.shopId())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm."));
    }
}
