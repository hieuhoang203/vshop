package vn.vshop.modules.merchant.repository.jdbc;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.merchant.entity.Shop;
import vn.vshop.modules.merchant.entity.ShopReview;
import vn.vshop.modules.merchant.entity.ShopStatus;
import vn.vshop.modules.merchant.repository.ShopRepository;

@Repository
public class JdbcShopRepository implements ShopRepository {
    private static final String COLUMNS =
            "s.id, s.owner_account_id, s.name, s.description, s.status, s.rejection_reason, s.version, s.created_at, s.updated_at";
    private final JdbcClient jdbc;

    public JdbcShopRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Shop> findOwnedBySubject(String subject) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + """
             FROM merchant.shop s JOIN identity.account a ON a.id = s.owner_account_id
             WHERE a.external_subject = :subject ORDER BY s.created_at DESC, s.id
            """)
                .param("subject", subject)
                .query(Shop.class)
                .list();
    }

    @Override
    public List<Shop> findSubmitted(int limit) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + """
             FROM merchant.shop s WHERE s.status = 'SUBMITTED'
             ORDER BY s.created_at, s.id LIMIT :limit
            """)
                .param("limit", limit)
                .query(Shop.class)
                .list();
    }

    @Override
    public Optional<Shop> findOwnedById(UUID id, String subject) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + """
             FROM merchant.shop s JOIN identity.account a ON a.id = s.owner_account_id
             WHERE s.id = :id AND a.external_subject = :subject
            """)
                .param("id", id)
                .param("subject", subject)
                .query(Shop.class)
                .optional();
    }

    @Override
    public Optional<Shop> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM merchant.shop s WHERE s.id = :id FOR UPDATE")
                .param("id", id)
                .query(Shop.class)
                .optional();
    }

    @Override
    public boolean insert(Shop shop) {
        return jdbc.sql(
                                """
            INSERT INTO merchant.shop (id, owner_account_id, name, description, status)
            VALUES (:id, :owner, :name, :description, :status)
            ON CONFLICT (owner_account_id) DO NOTHING
            """)
                        .param("id", shop.id())
                        .param("owner", shop.ownerAccountId())
                        .param("name", shop.name())
                        .param("description", shop.description())
                        .param("status", shop.status().name())
                        .update()
                == 1;
    }

    @Override
    public void addOwnerMembership(UUID shopId, UUID accountId) {
        jdbc.sql(
                        """
            INSERT INTO merchant.shop_member (shop_id, account_id, role)
            VALUES (:shop, :account, 'OWNER')
            """)
                .param("shop", shopId)
                .param("account", accountId)
                .update();
    }

    @Override
    public boolean resubmit(UUID id, String subject, String name, String description) {
        return jdbc.sql(
                                """
            UPDATE merchant.shop SET name = :name, description = :description, status = 'SUBMITTED',
                rejection_reason = NULL, updated_at = now(), version = version + 1
            WHERE id = :id AND status = 'REJECTED'
                AND owner_account_id = (SELECT id FROM identity.account WHERE external_subject = :subject)
            """)
                        .param("id", id)
                        .param("subject", subject)
                        .param("name", name)
                        .param("description", description)
                        .update()
                == 1;
    }

    @Override
    public void updateDecision(UUID id, ShopStatus status, String reason) {
        jdbc.sql(
                        """
            UPDATE merchant.shop SET status = :status, rejection_reason = :reason,
                updated_at = now(), version = version + 1 WHERE id = :id
            """)
                .param("id", id)
                .param("status", status.name())
                .param("reason", reason, Types.VARCHAR)
                .update();
    }

    @Override
    public java.util.Set<UUID> findMemberShops(UUID accountId, java.util.Collection<UUID> shopIds) {
        if (shopIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(
                jdbc.sql(
                                "SELECT shop_id FROM merchant.shop_member"
                                        + " WHERE account_id = :account AND shop_id IN (:shops)")
                        .param("account", accountId)
                        .param("shops", shopIds)
                        .query(UUID.class)
                        .list());
    }

    @Override
    public void insertReview(ShopReview review) {
        jdbc.sql(
                        """
            INSERT INTO merchant.shop_review (id, shop_id, reviewer_account_id, decision, reason)
            VALUES (:id, :shop, :reviewer, :decision, :reason)
            """)
                .param("id", review.id())
                .param("shop", review.shopId())
                .param("reviewer", review.reviewerAccountId())
                .param("decision", review.decision().name())
                .param("reason", review.reason())
                .update();
    }
}
