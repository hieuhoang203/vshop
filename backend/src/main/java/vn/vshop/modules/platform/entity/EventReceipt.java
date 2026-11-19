package vn.vshop.modules.platform.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventReceipt(UUID eventId, String eventType, OffsetDateTime receivedAt) {}
