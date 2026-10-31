CREATE TABLE transformation_plan_run (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    eligible_count INTEGER NOT NULL,
    applied_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    rolled_back_count INTEGER NOT NULL,
    restored_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_transformation_plan_run_plan
    ON transformation_plan_run (plan_id, created_at DESC);

CREATE TABLE transformation_plan_run_item (
    id UUID PRIMARY KEY,
    plan_run_id UUID NOT NULL REFERENCES transformation_plan_run (id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    item_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    run_id UUID,
    error_code VARCHAR(80),
    error_message VARCHAR(4000)
);

CREATE INDEX idx_transformation_plan_run_item_run
    ON transformation_plan_run_item (plan_run_id, ordinal);
