package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.Size;

/** {@code restock} puts the returned goods back into sellable stock. */
public record ConfirmReceivedRequest(boolean restock, @Size(max = 600) String note) {}
