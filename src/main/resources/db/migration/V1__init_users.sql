CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ========== users ==========
CREATE TABLE users (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid UUID NOT NULL DEFAULT gen_random_uuid(),
  username TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT NULL,
  deleted_at TIMESTAMPTZ NULL,
  deleted_by BIGINT NULL
);

CREATE INDEX ix_users_uuid_hash ON users USING hash (uuid);
CREATE UNIQUE INDEX ux_users_username_active ON users(username) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_created_at_active ON users(created_at) WHERE deleted_at IS NULL;
