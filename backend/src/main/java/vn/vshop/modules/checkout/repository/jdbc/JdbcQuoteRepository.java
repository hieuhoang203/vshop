package vn.vshop.modules.checkout.repository.jdbc;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.checkout.entity.Quote;
import vn.vshop.modules.checkout.repository.QuoteRepository;

@Repository
public class JdbcQuoteRepository implements QuoteRepository {
    private final JdbcClient jdbc;

    public JdbcQuoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Quote quote, long ttlSeconds) {
        jdbc.sql(
                        """
            INSERT INTO checkout.quote
                (id, buyer_account_id, cart_id, cart_version, total_amount, currency, snapshot, expires_at)
            VALUES (:id, :buyer, :cart, :version, :total, :currency, CAST(:snapshot AS jsonb),
                    now() + make_interval(secs => :ttl))
            """)
                .param("id", quote.id())
                .param("buyer", quote.buyerAccountId())
                .param("cart", quote.cartId())
                .param("version", quote.cartVersion())
                .param("total", quote.totalAmount())
                .param("currency", quote.currency())
                .param("snapshot", quote.snapshot())
                .param("ttl", (double) ttlSeconds)
                .update();
    }

    @Override
    public Optional<Quote> findForBuyer(UUID quoteId, UUID buyerAccountId) {
        return jdbc.sql(
                        """
            SELECT id, buyer_account_id, cart_id, cart_version, total_amount, currency,
                   snapshot::text AS snapshot, expires_at, created_at
            FROM checkout.quote WHERE id = :id AND buyer_account_id = :buyer
            """)
                .param("id", quoteId)
                .param("buyer", buyerAccountId)
                .query(Quote.class)
                .optional();
    }
}
