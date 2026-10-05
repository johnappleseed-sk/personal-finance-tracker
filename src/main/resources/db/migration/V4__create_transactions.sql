-- Composite foreign keys enforce both ownership and historical currency/type even outside JPA.
ALTER TABLE accounts ADD CONSTRAINT uk_accounts_owner_id_currency UNIQUE (user_id, id, currency);
ALTER TABLE categories ADD CONSTRAINT uk_categories_owner_id_type UNIQUE (user_id, id, category_type);

CREATE TABLE transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    account_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_owned_account FOREIGN KEY (user_id, account_id, currency)
        REFERENCES accounts (user_id, id, currency),
    CONSTRAINT fk_transactions_owned_category FOREIGN KEY (user_id, category_id, transaction_type)
        REFERENCES categories (user_id, id, category_type),
    CONSTRAINT ck_transactions_type CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_amount CHECK (amount BETWEEN 0.01 AND 99999999999999999.99)
);

CREATE INDEX ix_transactions_user_date_id ON transactions (user_id, transaction_date DESC, id DESC);
CREATE INDEX ix_transactions_account ON transactions (account_id);
CREATE INDEX ix_transactions_category ON transactions (category_id);
