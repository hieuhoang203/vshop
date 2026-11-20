package vn.vshop.modules.merchant.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.merchant.entity.Shop;
import vn.vshop.modules.merchant.entity.ShopReview;
import vn.vshop.modules.merchant.entity.ShopStatus;

public interface ShopRepository {
    List<Shop> findOwnedBySubject(String subject);

    List<Shop> findSubmitted(int limit);

    Optional<Shop> findOwnedById(UUID id, String subject);

    Optional<Shop> findByIdForUpdate(UUID id);

    boolean insert(Shop shop);

    void addOwnerMembership(UUID shopId, UUID accountId);

    boolean resubmit(UUID id, String subject, String name, String description);

    void updateDecision(UUID id, ShopStatus status, String rejectionReason);

    void insertReview(ShopReview review);

    /** The subset of {@code shopIds} the account is a member of. */
    java.util.Set<UUID> findMemberShops(UUID accountId, java.util.Collection<UUID> shopIds);
}
