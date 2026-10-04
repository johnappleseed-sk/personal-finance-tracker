-- Store password hashes only. Encoding and email normalization belong to registration.
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_users_email_normalized CHECK (
        length(email) > 0 AND email = lower(btrim(email))
    ),
    CONSTRAINT ck_users_password_hash_not_blank CHECK (length(btrim(password_hash)) > 0)
);

-- Future user services must update updated_at when a user's details change.
