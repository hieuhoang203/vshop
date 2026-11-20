package vn.vshop.modules.identity.model;

import java.util.UUID;

/**
 * The part of an owned address other modules may use: identity, version and delivery region. It
 * deliberately has no phone, street or recipient, so quotes and logs cannot leak them.
 */
public record ShipTo(UUID addressId, long version, String province, String ward) {}
