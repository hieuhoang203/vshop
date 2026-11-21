package vn.vshop.modules.order.entity;

import java.util.UUID;

/**
 * A line of the order named in a return case; {@code amount} is that quantity's share of the line.
 */
public record ReturnCaseItem(UUID orderLineId, String productName, int quantity, long amount) {}
