package vn.vshop.modules.order.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A return case with its history. Amounts are integer VND. */
public record ReturnCaseResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        String status,
        String reasonCode,
        String preferredResolution,
        @Nullable String resolution,
        String description,
        long requestedAmount,
        @Nullable Long offeredAmount,
        @Nullable Long approvedAmount,
        OffsetDateTime sellerDeadlineAt,
        @Nullable String sellerNote,
        @Nullable String adminNote,
        @Nullable String returnTrackingCode,
        OffsetDateTime createdAt,
        List<ReturnCaseEventResponse> events) {

    public record ReturnCaseEventResponse(
            @Nullable String fromStatus,
            String toStatus,
            String actorType,
            @Nullable String note,
            OffsetDateTime createdAt) {}
}
