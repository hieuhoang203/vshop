package vn.vshop.modules.identity.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A buyer address as stored: phone and street are encrypted envelopes, never plain text. Province
 * and ward stay readable because delivery zones are decided from them. {@code version} changes only
 * when the address content changes, not when it becomes or stops being the default.
 */
public record Address(
        UUID id,
        UUID accountId,
        String label,
        String recipientName,
        String phoneCiphertext,
        String streetCiphertext,
        String ward,
        String province,
        boolean isDefault,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
