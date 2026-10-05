CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    initial_balance NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_accounts_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('CHECKING', 'SAVINGS', 'CASH', 'CREDIT_CARD')),
    CONSTRAINT ck_accounts_currency CHECK (currency IN ('EUR', 'USD', 'GBP')),
    -- Negative opening balances represent overdrafts/debt. The range also rejects PostgreSQL NaN.
    CONSTRAINT ck_accounts_balance_finite CHECK (
        initial_balance BETWEEN -99999999999999999.99 AND 99999999999999999.99
    )
);

CREATE INDEX ix_accounts_user_name_id ON accounts (user_id, name, id);
