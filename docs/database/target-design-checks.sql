-- Meaningful checks of the reference financial DDL, isolated from the application DB.
BEGIN;
INSERT INTO finance.ledger_account (id, code, account_type) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'TEST_CASH', 'ASSET'),
    ('a0000000-0000-0000-0000-000000000002', 'TEST_PAYABLE', 'LIABILITY');
INSERT INTO finance.journal_entry (id, business_event_key)
VALUES ('b0000000-0000-0000-0000-000000000001', 'design-balanced-entry');
INSERT INTO finance.journal_line (entry_id, account_id, currency, debit, credit) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'VND', 100000, 0),
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 'VND', 0, 100000);
COMMIT;

DO $$
BEGIN
    BEGIN
        INSERT INTO finance.journal_entry (business_event_key) VALUES ('design-unbalanced-entry');
        SET CONSTRAINTS ALL IMMEDIATE;
        RAISE EXCEPTION 'TEST FAILED: unbalanced entry was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE finance.journal_line SET debit = debit + 1
        WHERE entry_id = 'b0000000-0000-0000-0000-000000000001';
        RAISE EXCEPTION 'TEST FAILED: mutation was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO finance.journal_line (entry_id, account_id, currency, debit, credit)
        VALUES ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'VND', 1, 0);
        RAISE EXCEPTION 'TEST FAILED: append to posted entry was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO finance.journal_entry (business_event_key) VALUES ('design-balanced-entry');
        RAISE EXCEPTION 'TEST FAILED: duplicate posting was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END;
$$;
SELECT 'reference_ledger_checks_passed' AS result;
