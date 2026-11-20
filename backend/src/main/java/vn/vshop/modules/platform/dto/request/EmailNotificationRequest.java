package vn.vshop.modules.platform.dto.request;

/** Turn email notifications on or off. The address is never part of the request. */
public record EmailNotificationRequest(boolean enabled) {}
