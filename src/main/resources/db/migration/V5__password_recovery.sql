ALTER TABLE user_accounts ADD COLUMN credential_revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE password_reset_tokens (
 token_hash VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES user_accounts(id),
 expires_at TIMESTAMPTZ NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX password_reset_user_idx ON password_reset_tokens(user_id);
CREATE TABLE password_reset_limits (email_hash VARCHAR(64) PRIMARY KEY, requested_at TIMESTAMPTZ NOT NULL);
CREATE TABLE password_reset_gate (id INTEGER PRIMARY KEY CHECK(id=1), window_start TIMESTAMPTZ NOT NULL, requests INTEGER NOT NULL);
INSERT INTO password_reset_gate VALUES(1,CURRENT_TIMESTAMP,0);
