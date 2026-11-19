package vn.vshop.modules.merchant.model;

import java.time.LocalDate;

public record ShopActivityPoint(LocalDate date, long submitted, long approved, long rejected) {}
