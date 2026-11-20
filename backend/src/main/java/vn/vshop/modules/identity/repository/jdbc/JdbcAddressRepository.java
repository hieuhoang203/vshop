package vn.vshop.modules.identity.repository.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.identity.entity.Address;
import vn.vshop.modules.identity.repository.AddressRepository;

@Repository
public class JdbcAddressRepository implements AddressRepository {
    private static final String COLUMNS =
            "id, account_id, label, recipient_name, phone_ciphertext, street_ciphertext, ward, province, is_default, version, created_at, updated_at";
    private final JdbcClient jdbc;

    public JdbcAddressRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockAccount(UUID accountId) {
        jdbc.sql("SELECT id FROM identity.account WHERE id = :id FOR UPDATE")
                .param("id", accountId)
                .query(UUID.class)
                .single();
    }

    @Override
    public List<Address> findByAccount(UUID accountId) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM identity.address WHERE account_id = :account ORDER BY is_default DESC, created_at, id")
                .param("account", accountId)
                .query(Address.class)
                .list();
    }

    @Override
    public Optional<Address> find(UUID accountId, UUID id) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM identity.address WHERE id = :id AND account_id = :account")
                .param("id", id)
                .param("account", accountId)
                .query(Address.class)
                .optional();
    }

    @Override
    public long count(UUID accountId) {
        return jdbc.sql("SELECT count(*) FROM identity.address WHERE account_id = :account")
                .param("account", accountId)
                .query(Long.class)
                .single();
    }

    @Override
    public void insert(Address address) {
        jdbc.sql(
                        """
            INSERT INTO identity.address
                (id, account_id, label, recipient_name, phone_ciphertext, street_ciphertext, ward, province, is_default)
            VALUES (:id, :account, :label, :recipient, :phone, :street, :ward, :province, :isDefault)
            """)
                .param("id", address.id())
                .param("account", address.accountId())
                .param("label", address.label(), java.sql.Types.VARCHAR)
                .param("recipient", address.recipientName())
                .param("phone", address.phoneCiphertext())
                .param("street", address.streetCiphertext())
                .param("ward", address.ward())
                .param("province", address.province())
                .param("isDefault", address.isDefault())
                .update();
    }

    @Override
    public boolean updateContent(Address address, long expectedVersion) {
        return jdbc.sql(
                                """
            UPDATE identity.address SET label = :label, recipient_name = :recipient,
                phone_ciphertext = :phone, street_ciphertext = :street, ward = :ward, province = :province,
                updated_at = now(), version = version + 1
            WHERE id = :id AND account_id = :account AND version = :version
            """)
                        .param("id", address.id())
                        .param("account", address.accountId())
                        .param("label", address.label(), java.sql.Types.VARCHAR)
                        .param("recipient", address.recipientName())
                        .param("phone", address.phoneCiphertext())
                        .param("street", address.streetCiphertext())
                        .param("ward", address.ward())
                        .param("province", address.province())
                        .param("version", expectedVersion)
                        .update()
                == 1;
    }

    @Override
    public boolean delete(UUID accountId, UUID id) {
        return jdbc.sql("DELETE FROM identity.address WHERE id = :id AND account_id = :account")
                        .param("id", id)
                        .param("account", accountId)
                        .update()
                == 1;
    }

    @Override
    public void clearDefault(UUID accountId) {
        jdbc.sql(
                        "UPDATE identity.address SET is_default = FALSE, updated_at = now() WHERE account_id = :account AND is_default")
                .param("account", accountId)
                .update();
    }

    @Override
    public void markDefault(UUID accountId, UUID id) {
        jdbc.sql(
                        "UPDATE identity.address SET is_default = TRUE, updated_at = now() WHERE id = :id AND account_id = :account")
                .param("id", id)
                .param("account", accountId)
                .update();
    }

    @Override
    public Optional<Address> findNewest(UUID accountId) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM identity.address WHERE account_id = :account ORDER BY created_at DESC, id LIMIT 1")
                .param("account", accountId)
                .query(Address.class)
                .optional();
    }
}
