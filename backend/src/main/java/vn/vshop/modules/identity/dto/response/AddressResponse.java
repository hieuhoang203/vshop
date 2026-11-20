package vn.vshop.modules.identity.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AddressResponse(
        UUID id,
        @Nullable String label,
        String recipientName,
        String phone,
        String street,
        String ward,
        String province,
        boolean isDefault,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
