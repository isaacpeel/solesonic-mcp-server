CREATE TABLE comfy_workflow (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(128) NOT NULL,
    tool_name     VARCHAR(128) NOT NULL UNIQUE,
    description   TEXT         NOT NULL,
    workflow_json TEXT         NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE comfy_workflow OWNER TO "${DB_OWNER}";
