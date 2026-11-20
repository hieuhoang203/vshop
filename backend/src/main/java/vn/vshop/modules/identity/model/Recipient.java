package vn.vshop.modules.identity.model;

import java.util.UUID;

/**
 * Decrypted delivery details of an owned address, for an order snapshot. Callers must re-encrypt
 * before storing and never log or publish it.
 */
public record Recipient(
        UUID addressId,
        long version,
        String name,
        String phone,
        String street,
        String ward,
        String province) {}
