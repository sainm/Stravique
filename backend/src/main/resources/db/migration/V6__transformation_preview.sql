CREATE TABLE transformation_proposal (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES migration_plan(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES migration_item(id) ON DELETE CASCADE,
    source_file_id UUID NOT NULL REFERENCES project_file(id) ON DELETE CASCADE,
    relative_path VARCHAR(4096) NOT NULL,
    rule_id VARCHAR(120) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    original_sha256 VARCHAR(64) NOT NULL,
    proposed_sha256 VARCHAR(64) NOT NULL,
    diff TEXT NOT NULL,
    validation_status VARCHAR(20) NOT NULL,
    validation_message VARCHAR(4000),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE,
    restored_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_transformation_proposal_item ON transformation_proposal(plan_id, item_id);
CREATE INDEX idx_transformation_proposal_status ON transformation_proposal(plan_id, status);

CREATE TABLE transformation_run (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES migration_plan(id) ON DELETE CASCADE,
    proposal_id UUID NOT NULL REFERENCES transformation_proposal(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES migration_item(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    relative_path VARCHAR(4096) NOT NULL,
    backup_path VARCHAR(4096),
    original_sha256 VARCHAR(64) NOT NULL,
    applied_sha256 VARCHAR(64) NOT NULL,
    rule_id VARCHAR(120) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    error_message VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_transformation_run_item ON transformation_run(plan_id, item_id);
CREATE INDEX idx_transformation_run_proposal ON transformation_run(proposal_id);
