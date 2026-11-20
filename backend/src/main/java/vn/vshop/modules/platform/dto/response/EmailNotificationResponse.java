package vn.vshop.modules.platform.dto.response;

import org.jspecify.annotations.Nullable;

/**
 * {@code available} is false while the platform has no mail server configured, in which case
 * turning the channel on has no effect yet. The address is masked.
 */
public record EmailNotificationResponse(
        boolean enabled, @Nullable String maskedEmail, boolean available) {}
