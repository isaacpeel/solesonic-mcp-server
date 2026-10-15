-- The classified intents each workflow serves. The image router offers a workflow for a request only
-- when it serves the request's intent, or when no workflow that can take the request does.
CREATE TABLE comfy_workflow_intent (
    comfy_workflow_id UUID        NOT NULL REFERENCES comfy_workflow (id) ON DELETE CASCADE,
    intent            VARCHAR(32) NOT NULL CHECK (intent IN ('NEW_IMAGE', 'RESTYLE_PHOTO', 'EDIT_PERSON_IN_PHOTO')),
    PRIMARY KEY (comfy_workflow_id, intent)
);

ALTER TABLE comfy_workflow_intent OWNER TO "${DB_OWNER}";
