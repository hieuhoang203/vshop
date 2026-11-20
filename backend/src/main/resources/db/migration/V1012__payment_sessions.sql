-- Providers such as payOS create the hosted payment page over the network, after the order is
-- committed. The page URL is stored on the attempt once known, and payOS needs a unique integer
-- order code per attempt. Additive only.
ALTER TABLE payment.attempt ADD COLUMN checkout_url TEXT;
CREATE SEQUENCE payment.order_code_seq START WITH 10000000;
