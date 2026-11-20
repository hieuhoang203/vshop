package vn.vshop.modules.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A decrypted address, only ever handed to its owner. */
public record AddressDetails(
        UUID id,
        String label,
        String recipientName,
        String phone,
        String street,
        String ward,
        String province,
        boolean isDefault,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
