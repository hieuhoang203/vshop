package vn.vshop.modules.platform.repository.jdbc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.entity.PolicyVersion;
import vn.vshop.modules.platform.repository.PolicyRepository;

@Repository
public class JdbcPolicyRepository implements PolicyRepository {
    private static final String COLUMNS =
            """
            id, policy_type, version, content::text AS content, status, effective_from, effective_to,
            created_by_account_id, approved_by_account_id, approved_at, created_at
            """;
    private final JdbcClient jdbc;

    public JdbcPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PolicyVersion insertDraft(PolicyType type, String contentJson, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
            INSERT INTO platform.policy_version (id, policy_type, version, content, created_by_account_id)
            VALUES (:id, :type,
                    (SELECT COALESCE(MAX(version), 0) + 1 FROM platform.policy_version WHERE policy_type = :type),
                    CAST(:content AS jsonb), :creator)
            """)
                .param("id", id)
                .param("type", type.name())
                .param("content", contentJson)
                .param("creator", createdBy)
                .update();
        return jdbc.sql("SELECT " + COLUMNS + " FROM platform.policy_version WHERE id = :id")
                .param("id", id)
                .query(PolicyVersion.class)
                .single();
    }

    @Override
    public Optional<PolicyVersion> findForUpdate(UUID id) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM platform.policy_version WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(PolicyVersion.class)
                .optional();
    }

    @Override
    public Optional<PolicyVersion> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM platform.policy_version WHERE id = :id")
                .param("id", id)
                .query(PolicyVersion.class)
                .optional();
    }

    @Override
    public Optional<PolicyVersion> findCurrent(PolicyType type) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM platform.policy_version WHERE policy_type = :type AND status = 'APPROVED'")
                .param("type", type.name())
                .query(PolicyVersion.class)
                .optional();
    }

    @Override
    public List<PolicyVersion> list() {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM platform.policy_version ORDER BY policy_type, version DESC")
                .query(PolicyVersion.class)
                .list();
    }

    @Override
    public void retireCurrent(PolicyType type, OffsetDateTime at) {
        jdbc.sql(
                        """
            UPDATE platform.policy_version SET status = 'RETIRED', effective_to = :at
            WHERE policy_type = :type AND status = 'APPROVED'
            """)
                .param("type", type.name())
                .param("at", at)
                .update();
    }

    @Override
    public void approve(UUID id, UUID approver, OffsetDateTime at) {
        jdbc.sql(
                        """
            UPDATE platform.policy_version
            SET status = 'APPROVED', effective_from = :at, approved_by_account_id = :approver, approved_at = :at
            WHERE id = :id AND status = 'DRAFT'
            """)
                .param("id", id)
                .param("approver", approver)
                .param("at", at)
                .update();
    }
}
