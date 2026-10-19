-- Allow KHR in accounts currency constraint alongside EUR, USD, GBP.
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_currency;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_currency CHECK (currency IN ('EUR', 'USD', 'GBP', 'KHR'));

-- User-owned monthly expense budgets per category and currency.
CREATE TABLE budgets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    currency VARCHAR(3) NOT NULL,
    budget_month VARCHAR(7) NOT NULL,
    limit_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_budgets_user_category_currency_month UNIQUE (user_id, category_id, currency, budget_month),
    CONSTRAINT ck_budgets_currency CHECK (currency IN ('EUR', 'USD', 'GBP', 'KHR')),
    CONSTRAINT ck_budgets_month CHECK (budget_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_budgets_amount CHECK (limit_amount BETWEEN 0.01 AND 99999999999999999.99)
);

CREATE INDEX ix_budgets_user_month ON budgets (user_id, budget_month DESC, id DESC);

-- Same-currency transfers between two accounts owned by the same user.
CREATE TABLE transfers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transfer_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfers_source_account FOREIGN KEY (user_id, source_account_id, currency)
        REFERENCES accounts (user_id, id, currency),
    CONSTRAINT fk_transfers_target_account FOREIGN KEY (user_id, target_account_id, currency)
        REFERENCES accounts (user_id, id, currency),
    CONSTRAINT ck_transfers_different_accounts CHECK (source_account_id <> target_account_id),
    CONSTRAINT ck_transfers_amount CHECK (amount BETWEEN 0.01 AND 99999999999999999.99)
);

CREATE INDEX ix_transfers_user_date_id ON transfers (user_id, transfer_date DESC, id DESC);
CREATE INDEX ix_transfers_source_account ON transfers (source_account_id);
CREATE INDEX ix_transfers_target_account ON transfers (target_account_id);
