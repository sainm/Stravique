CREATE TABLE module_dependency (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    from_module_id UUID NOT NULL REFERENCES project_module(id) ON DELETE CASCADE,
    to_module_id UUID NOT NULL REFERENCES project_module(id) ON DELETE CASCADE,
    dependency_type VARCHAR(30) NOT NULL,
    evidence_path VARCHAR(4096) NOT NULL,
    line_start INTEGER,
    rule_id VARCHAR(120) NOT NULL,
    certainty VARCHAR(30) NOT NULL,
    analyzer_version VARCHAR(40) NOT NULL,
    analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (scan_id, from_module_id, to_module_id, evidence_path, line_start)
);

CREATE INDEX idx_module_dependency_scan ON module_dependency(scan_id);
CREATE INDEX idx_module_dependency_from ON module_dependency(scan_id, from_module_id);
