-- PostgreSQL schema for encrypted tokens
CREATE TABLE jira_tokens (
  user_profile_id VARCHAR(200) NOT NULL,
  cloud_id        VARCHAR(200) NOT NULL,
  encrypted_payload TEXT       NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_jira_tokens PRIMARY KEY (user_profile_id, cloud_id)
);

-- Optional indexes for lookups (composite PK already covers most cases)
-- CREATE INDEX idx_jira_tokens_user ON jira_tokens (user_profile_id);
