package vn.vshop.modules.identity.model;

/** Raw address fields from the owner; the service validates and normalises them. */
public record AddressInput(
        String label,
        String recipientName,
        String phone,
        String street,
        String ward,
        String province,
        boolean makeDefault) {}
