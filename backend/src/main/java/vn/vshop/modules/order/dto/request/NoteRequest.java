package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.Size;

/** An optional free-text note. */
public record NoteRequest(@Size(max = 600) String note) {}
