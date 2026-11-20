package vn.vshop.modules.platform.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.entity.PolicyVersion;

public interface PolicyRepository {
    /** Inserts the next version of the type as DRAFT and returns it. */
    PolicyVersion insertDraft(PolicyType type, String contentJson, UUID createdBy);

    Optional<PolicyVersion> findForUpdate(UUID id);

    Optional<PolicyVersion> find(UUID id);

    Optional<PolicyVersion> findCurrent(PolicyType type);

    List<PolicyVersion> list();

    void retireCurrent(PolicyType type, OffsetDateTime at);

    void approve(UUID id, UUID approver, OffsetDateTime at);
}
