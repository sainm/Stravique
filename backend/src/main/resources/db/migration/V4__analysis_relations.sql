CREATE TABLE analysis_relation (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    source_file_id UUID NOT NULL REFERENCES project_file(id) ON DELETE CASCADE,
    target_file_id UUID NOT NULL REFERENCES project_file(id) ON DELETE CASCADE,
    source_object_key VARCHAR(512) NOT NULL,
    target_object_key VARCHAR(512) NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    evidence_path VARCHAR(4096) NOT NULL,
    line_start INTEGER,
    evidence VARCHAR(4000) NOT NULL,
    rule_id VARCHAR(120) NOT NULL,
    certainty VARCHAR(30) NOT NULL,
    analyzer_version VARCHAR(40) NOT NULL,
    analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (scan_id, source_file_id, target_file_id, relation_type, evidence_path, line_start)
);

CREATE INDEX idx_analysis_relation_scan ON analysis_relation(scan_id);
CREATE INDEX idx_analysis_relation_source ON analysis_relation(scan_id, source_file_id);
CREATE INDEX idx_analysis_relation_target ON analysis_relation(scan_id, target_file_id);
