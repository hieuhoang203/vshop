package vn.vshop.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.vshop.modules.merchant.model.ShopActivityPoint;
import vn.vshop.modules.merchant.repository.MerchantAnalyticsRepository;
import vn.vshop.modules.merchant.service.MerchantAnalyticsService;

class MerchantAnalyticsServiceTests {
    @Test
    void reportingUsesVietnamCalendarDayAcrossUtcMidnight() {
        var repository = mock(MerchantAnalyticsRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-11-18T18:30:00Z"), ZoneOffset.UTC);
        var from = LocalDate.of(2026, 11, 13);
        var to = LocalDate.of(2026, 11, 19);
        when(repository.findDailyActivity(from, to, "Asia/Ho_Chi_Minh"))
                .thenReturn(List.of(new ShopActivityPoint(to, 2, 1, 0)));
        var report = new MerchantAnalyticsService(repository, clock).report(7);
        assertThat(report.from()).isEqualTo(from);
        assertThat(report.to()).isEqualTo(to);
        assertThat(report.points().getFirst().submitted()).isEqualTo(2);
        verify(repository).findDailyActivity(from, to, "Asia/Ho_Chi_Minh");
    }

    @Test
    void reportingRejectsUnboundedWindowsEvenOutsideTheController() {
        var service =
                new MerchantAnalyticsService(
                        mock(MerchantAnalyticsRepository.class), Clock.systemUTC());
        assertThatThrownBy(() -> service.report(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(91)).isInstanceOf(IllegalArgumentException.class);
    }
}
