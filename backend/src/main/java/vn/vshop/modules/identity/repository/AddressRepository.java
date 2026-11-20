package vn.vshop.modules.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.identity.entity.Address;

/** Every method is scoped by account, so an address id alone never reaches another buyer's data. */
public interface AddressRepository {
    /** Serialises address changes of one account (limit and default checks). */
    void lockAccount(UUID accountId);

    List<Address> findByAccount(UUID accountId);

    Optional<Address> find(UUID accountId, UUID id);

    long count(UUID accountId);

    void insert(Address address);

    /** Replaces content and bumps the version; false when the expected version is stale. */
    boolean updateContent(Address address, long expectedVersion);

    boolean delete(UUID accountId, UUID id);

    void clearDefault(UUID accountId);

    void markDefault(UUID accountId, UUID id);

    Optional<Address> findNewest(UUID accountId);
}
