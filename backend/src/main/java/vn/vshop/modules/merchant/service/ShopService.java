package vn.vshop.modules.merchant.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.merchant.entity.Shop;
import vn.vshop.modules.merchant.entity.ShopReview;
import vn.vshop.modules.merchant.entity.ShopReviewDecision;
import vn.vshop.modules.merchant.entity.ShopStatus;
import vn.vshop.modules.merchant.repository.ShopRepository;
import vn.vshop.modules.platform.service.AuditEventService;

@Service
@Transactional(readOnly = true)
public class ShopService {
    private final ShopRepository shops;
    private final AccountService accounts;
    private final AuditEventService events;

    public ShopService(ShopRepository shops, AccountService accounts, AuditEventService events) {
        this.shops = shops;
        this.accounts = accounts;
        this.events = events;
    }

    public List<Shop> owned(String subject) {
        return shops.findOwnedBySubject(subject);
    }

    /**
     * The subset of {@code shopIds} the account works for (any role), e.g. to forbid self-purchase.
     */
    public java.util.Set<UUID> memberShops(UUID accountId, java.util.Collection<UUID> shopIds) {
        return shops.findMemberShops(accountId, shopIds);
    }

    public List<Shop> reviewQueue() {
        return shops.findSubmitted(100);
    }

    public Shop ownedShop(UUID id, String subject) {
        return shops.findOwnedById(id, subject).orElseThrow(this::shopNotFound);
    }

    @Transactional
    public Shop submit(String subject, String name, String description) {
        UUID accountId = accounts.requireActiveAccount(subject);
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        Shop shop =
                new Shop(
                        id,
                        accountId,
                        name.strip(),
                        description.strip(),
                        ShopStatus.SUBMITTED,
                        null,
                        0,
                        now,
                        now);
        if (!shops.insert(shop)) {
            throw new BusinessException(
                    ErrorCode.SHOP_ALREADY_EXISTS,
                    "Bạn đã có hồ sơ gian hàng. Vui lòng theo dõi hồ sơ hiện tại.");
        }
        shops.addOwnerMembership(id, accountId);
        events.record(subject, "ShopSubmitted", id, "Seller submitted shop application");
        return ownedShop(id, subject);
    }

    @Transactional
    public Shop resubmit(UUID id, String subject, String name, String description) {
        accounts.requireActiveAccount(subject);
        if (!shops.resubmit(id, subject, name.strip(), description.strip())) {
            ownedShop(id, subject);
            throw new BusinessException(
                    ErrorCode.INVALID_SHOP_STATE, "Hồ sơ hiện tại không thể gửi lại.");
        }
        events.record(subject, "ShopResubmitted", id, "Seller updated rejected application");
        return ownedShop(id, subject);
    }

    @Transactional
    public Shop review(UUID id, String subject, ShopReviewDecision decision, String reason) {
        UUID reviewer = accounts.requireActiveAccount(subject);
        Shop current = shops.findByIdForUpdate(id).orElseThrow(this::shopNotFound);
        if (current.status() != ShopStatus.SUBMITTED) {
            throw new BusinessException(
                    ErrorCode.INVALID_SHOP_STATE,
                    "Hồ sơ đã được xử lý. Vui lòng tải lại danh sách.");
        }
        ShopStatus status = ShopStatus.valueOf(decision.name());
        String rejectedReason = status == ShopStatus.REJECTED ? reason.strip() : null;
        shops.updateDecision(id, status, rejectedReason);
        shops.insertReview(
                new ShopReview(
                        UUID.randomUUID(),
                        id,
                        reviewer,
                        decision,
                        reason.strip(),
                        OffsetDateTime.now(java.time.ZoneOffset.UTC)));
        events.record(
                subject,
                decision == ShopReviewDecision.APPROVED ? "ShopApproved" : "ShopRejected",
                id,
                reason.strip());
        return shops.findByIdForUpdate(id).orElseThrow(this::shopNotFound);
    }

    private BusinessException shopNotFound() {
        return new BusinessException(ErrorCode.SHOP_NOT_FOUND, "Không tìm thấy gian hàng.");
    }
}
