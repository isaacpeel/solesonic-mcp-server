-- One image-guided graph per number of reference images, so a single workflow (tool) can accept
-- one, two or three photos. Replaces comfy_workflow.reference_workflow_json, which held one graph.
CREATE TABLE comfy_workflow_reference_variant (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    comfy_workflow_id UUID        NOT NULL REFERENCES comfy_workflow (id) ON DELETE CASCADE,
    image_count       INTEGER     NOT NULL CHECK (image_count BETWEEN 1 AND 3),
    workflow_json     TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (comfy_workflow_id, image_count)
);

ALTER TABLE comfy_workflow_reference_variant OWNER TO "${DB_OWNER}";

-- Carry every existing graph across, keyed by how many distinct __REFERENCE_IMAGE_n__ slots it loads.
-- A graph with none, or more than three, fails the CHECK above and stops the migration rather than
-- being dropped silently.
INSERT INTO comfy_workflow_reference_variant (comfy_workflow_id, image_count, workflow_json)
SELECT comfy_workflow.id,
       (SELECT count(DISTINCT slot_match[1])
        FROM regexp_matches(comfy_workflow.reference_workflow_json, '__REFERENCE_IMAGE_(\d+)__', 'g') AS slot_match),
       comfy_workflow.reference_workflow_json
FROM comfy_workflow
WHERE comfy_workflow.reference_workflow_json IS NOT NULL
  AND btrim(comfy_workflow.reference_workflow_json) <> '';

ALTER TABLE comfy_workflow DROP COLUMN reference_workflow_json;
