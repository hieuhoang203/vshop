package vn.vshop.modules.merchant.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.merchant.model.ShopActivityReport;
import vn.vshop.modules.merchant.repository.MerchantAnalyticsRepository;

@Service
@Transactional(readOnly = true)
public class MerchantAnalyticsService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MerchantAnalyticsRepository repository;
    private final Clock clock;

    public MerchantAnalyticsService(MerchantAnalyticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ShopActivityReport report(int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("Days must be in [1,90]");
        LocalDate to = LocalDate.now(clock.withZone(REPORT_ZONE));
        LocalDate from = to.minusDays(days - 1L);
        return new ShopActivityReport(
                from,
                to,
                REPORT_ZONE.getId(),
                repository.findDailyActivity(from, to, REPORT_ZONE.getId()));
    }
}
