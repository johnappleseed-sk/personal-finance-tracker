-- Categories are private to their owner; names need not be globally unique.
CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    name VARCHAR(100) NOT NULL,
    category_type VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_categories_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_categories_type CHECK (category_type IN ('INCOME', 'EXPENSE'))
);

CREATE INDEX ix_categories_user_name_id ON categories (user_id, name, id);
