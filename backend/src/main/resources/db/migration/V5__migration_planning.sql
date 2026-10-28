CREATE TABLE migration_plan (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    plan_sequence INTEGER NOT NULL,
    source_profile VARCHAR(200) NOT NULL,
    target_profile VARCHAR(40) NOT NULL,
    source_scan_sequence INTEGER NOT NULL,
    item_count INTEGER NOT NULL DEFAULT 0,
    included_count INTEGER NOT NULL DEFAULT 0,
    ready_count INTEGER NOT NULL DEFAULT 0,
    review_count INTEGER NOT NULL DEFAULT 0,
    blocked_count INTEGER NOT NULL DEFAULT 0,
    dependency_count INTEGER NOT NULL DEFAULT 0,
    phase_count INTEGER NOT NULL DEFAULT 0,
    analyzer_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (project_id, plan_sequence)
);

CREATE INDEX idx_migration_plan_project ON migration_plan(project_id, plan_sequence DESC);

CREATE TABLE migration_item (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES migration_plan(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    item_key VARCHAR(512) NOT NULL,
    item_type VARCHAR(40) NOT NULL,
    target_key VARCHAR(40) NOT NULL,
    title VARCHAR(512) NOT NULL,
    primary_file_id UUID REFERENCES project_file(id) ON DELETE SET NULL,
    primary_finding_id UUID REFERENCES analysis_finding(id) ON DELETE SET NULL,
    phase INTEGER NOT NULL,
    priority INTEGER NOT NULL,
    automation_level VARCHAR(20) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    included BOOLEAN NOT NULL,
    reason_code VARCHAR(60) NOT NULL,
    finding_count INTEGER NOT NULL DEFAULT 0,
    dependency_count INTEGER NOT NULL DEFAULT 0,
    dependent_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (plan_id, item_key)
);

CREATE INDEX idx_migration_item_plan ON migration_item(plan_id, phase, priority);
CREATE INDEX idx_migration_item_status ON migration_item(plan_id, status);
CREATE INDEX idx_migration_item_file ON migration_item(primary_file_id);

CREATE TABLE migration_item_finding (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES migration_item(id) ON DELETE CASCADE,
    finding_id UUID NOT NULL REFERENCES analysis_finding(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    UNIQUE (item_id, finding_id)
);

CREATE INDEX idx_migration_item_finding_finding ON migration_item_finding(finding_id);

CREATE TABLE migration_dependency (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES migration_plan(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    from_item_id UUID NOT NULL REFERENCES migration_item(id) ON DELETE CASCADE,
    to_item_id UUID NOT NULL REFERENCES migration_item(id) ON DELETE CASCADE,
    relation_id UUID REFERENCES analysis_relation(id) ON DELETE CASCADE,
    relation_type VARCHAR(40) NOT NULL,
    evidence_path VARCHAR(4096) NOT NULL,
    line_start INTEGER,
    certainty VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (plan_id, from_item_id, to_item_id, relation_type, evidence_path, line_start)
);

CREATE INDEX idx_migration_dependency_plan ON migration_dependency(plan_id);
CREATE INDEX idx_migration_dependency_from ON migration_dependency(plan_id, from_item_id);
CREATE INDEX idx_migration_dependency_to ON migration_dependency(plan_id, to_item_id);
