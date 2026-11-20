package vn.vshop.modules.order.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Where a carrier collects the parcels of a shop. Phone and street are PiiCipher envelopes. */
public record PickupAddress(
        UUID shopId,
        String contactName,
        String phoneCiphertext,
        String streetCiphertext,
        String ward,
        String district,
        String province,
        OffsetDateTime updatedAt) {}
