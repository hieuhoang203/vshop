package vn.vshop.modules.merchant.model;

import java.time.LocalDate;
import java.util.List;

public record ShopActivityReport(
        LocalDate from, LocalDate to, String timezone, List<ShopActivityPoint> points) {}
