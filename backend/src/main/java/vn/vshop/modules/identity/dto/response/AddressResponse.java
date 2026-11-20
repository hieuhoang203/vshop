package vn.vshop.modules.identity.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AddressResponse(
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
