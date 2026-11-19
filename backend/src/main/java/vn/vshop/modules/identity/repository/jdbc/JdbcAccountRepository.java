package vn.vshop.modules.identity.repository.jdbc;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.vshop.modules.identity.entity.Account;
import vn.vshop.modules.identity.repository.AccountRepository;

@Repository
public class JdbcAccountRepository implements AccountRepository {
    private final JdbcClient jdbc;

    public JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Account findOrCreate(String subject) {
        jdbc.sql(
                        """
            INSERT INTO identity.account (external_subject)
            VALUES (:subject)
            ON CONFLICT (external_subject) DO NOTHING
            """)
                .param("subject", subject)
                .update();
        return jdbc.sql(
                        """
            SELECT id, external_subject, status, created_at, updated_at
            FROM identity.account WHERE external_subject = :subject
            """)
                .param("subject", subject)
                .query(Account.class)
                .single();
    }
}
