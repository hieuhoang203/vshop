package vn.vshop.modules.merchant.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ShopActivityResponse(
        LocalDate from, LocalDate to, String timezone, List<Point> points) {
    public record Point(LocalDate date, long submitted, long approved, long rejected) {}
}
