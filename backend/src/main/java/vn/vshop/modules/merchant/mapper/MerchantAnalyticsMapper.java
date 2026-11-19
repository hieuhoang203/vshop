package vn.vshop.modules.merchant.mapper;

import vn.vshop.modules.merchant.dto.response.ShopActivityResponse;
import vn.vshop.modules.merchant.model.ShopActivityReport;

public final class MerchantAnalyticsMapper {
    private MerchantAnalyticsMapper() {}

    public static ShopActivityResponse toResponse(ShopActivityReport report) {
        return new ShopActivityResponse(
                report.from(),
                report.to(),
                report.timezone(),
                report.points().stream()
                        .map(
                                p ->
                                        new ShopActivityResponse.Point(
                                                p.date(),
                                                p.submitted(),
                                                p.approved(),
                                                p.rejected()))
                        .toList());
    }
}
