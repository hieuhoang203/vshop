package vn.vshop.modules.merchant.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShopMember(
        UUID shopId,
        UUID accountId,
        ShopMemberRole role,
        UUID ownerLinkAccountId,
        OffsetDateTime createdAt) {}
