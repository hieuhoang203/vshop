package vn.vshop.modules.merchant.repository.jdbc;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.merchant.model.ShopActivityPoint;
import vn.vshop.modules.merchant.repository.MerchantAnalyticsRepository;

@Repository
public class JdbcMerchantAnalyticsRepository implements MerchantAnalyticsRepository {
    private final JdbcClient jdbc;

    public JdbcMerchantAnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ShopActivityPoint> findDailyActivity(
            LocalDate from, LocalDate to, String timezone) {
        return jdbc.sql(
                        """
            WITH days AS (
                SELECT generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day')::date AS date
            ), applications AS (
                SELECT (created_at AT TIME ZONE :zone)::date AS date, count(*) AS submitted
                FROM merchant.shop
                WHERE created_at >= (CAST(:from AS date)::timestamp AT TIME ZONE :zone)
                  AND created_at < ((CAST(:to AS date) + 1)::timestamp AT TIME ZONE :zone)
                GROUP BY 1
            ), decisions AS (
                SELECT (created_at AT TIME ZONE :zone)::date AS date,
                    count(*) FILTER (WHERE decision = 'APPROVED') AS approved,
                    count(*) FILTER (WHERE decision = 'REJECTED') AS rejected
                FROM merchant.shop_review
                WHERE created_at >= (CAST(:from AS date)::timestamp AT TIME ZONE :zone)
                  AND created_at < ((CAST(:to AS date) + 1)::timestamp AT TIME ZONE :zone)
                GROUP BY 1
            )
            SELECT d.date, coalesce(a.submitted, 0) AS submitted,
                coalesce(r.approved, 0) AS approved, coalesce(r.rejected, 0) AS rejected
            FROM days d LEFT JOIN applications a ON a.date = d.date
            LEFT JOIN decisions r ON r.date = d.date ORDER BY d.date
            """)
                .param("from", from)
                .param("to", to)
                .param("zone", timezone)
                .query(ShopActivityPoint.class)
                .list();
    }
}
