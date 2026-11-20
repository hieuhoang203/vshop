package vn.vshop.modules.identity.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEmailRepository {
    record Row(
            UUID accountId, String emailCiphertext, boolean enabled, OffsetDateTime verifiedAt) {}

    Optional<Row> find(UUID accountId);

    /** Stores the address (an envelope) and turns the channel on. */
    void enable(UUID accountId, String emailCiphertext);

    void disable(UUID accountId);
}
