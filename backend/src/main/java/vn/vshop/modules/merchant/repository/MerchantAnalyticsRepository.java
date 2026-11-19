package vn.vshop.modules.merchant.repository;

import java.time.LocalDate;
import java.util.List;
import vn.vshop.modules.merchant.model.ShopActivityPoint;

public interface MerchantAnalyticsRepository {
    List<ShopActivityPoint> findDailyActivity(LocalDate from, LocalDate to, String timezone);
}
