package vn.vshop.modules.checkout.repository;

import java.util.Optional;
import java.util.UUID;
import vn.vshop.modules.checkout.entity.Quote;

public interface QuoteRepository {
    /** Created and expiry times come from the database clock; expiry is now() + ttlSeconds. */
    void insert(Quote quote, long ttlSeconds);

    /** Scoped by buyer so a quote of another account is indistinguishable from a missing one. */
    Optional<Quote> findForBuyer(UUID quoteId, UUID buyerAccountId);
}
