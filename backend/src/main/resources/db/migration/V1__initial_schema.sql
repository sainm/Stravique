CREATE TABLE analysis_project (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    source_path VARCHAR(4096) NOT NULL,
    project_type VARCHAR(40) NOT NULL,
    jdk_version VARCHAR(32),
    build_tool VARCHAR(20) NOT NULL,
    source_framework VARCHAR(160),
    target_framework VARCHAR(160),
    excluded_directories VARCHAR(4000) NOT NULL,
    excluded_files VARCHAR(4000) NOT NULL,
    analysis_scope VARCHAR(40) NOT NULL,
    last_scan_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE path_override (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    relative_path VARCHAR(2048) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (project_id, relative_path, role)
);

CREATE TABLE scan_run (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_sequence INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    file_count INTEGER NOT NULL DEFAULT 0,
    module_count INTEGER NOT NULL DEFAULT 0,
    analyzed_count INTEGER NOT NULL DEFAULT 0,
    parse_failure_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    finding_count INTEGER NOT NULL DEFAULT 0,
    review_count INTEGER NOT NULL DEFAULT 0,
    technology_count INTEGER NOT NULL DEFAULT 0,
    removed_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(80),
    error_message VARCHAR(4000),
    analyzer_version VARCHAR(40) NOT NULL,
    UNIQUE (project_id, scan_sequence)
);

CREATE INDEX idx_scan_run_project_created ON scan_run(project_id, created_at DESC);

CREATE TABLE project_module (
    id UUID PRIMARY KEY,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    relative_path VARCHAR(2048) NOT NULL,
    name VARCHAR(255) NOT NULL,
    source_roots VARCHAR(4000) NOT NULL,
    test_roots VARCHAR(4000) NOT NULL,
    resource_roots VARCHAR(4000) NOT NULL,
    web_roots VARCHAR(4000) NOT NULL,
    config_roots VARCHAR(4000) NOT NULL,
    sql_roots VARCHAR(4000) NOT NULL,
    UNIQUE (scan_id, relative_path)
);

CREATE INDEX idx_project_module_scan ON project_module(scan_id);

CREATE TABLE project_file (
    id UUID PRIMARY KEY,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    module_id UUID REFERENCES project_module(id) ON DELETE SET NULL,
    relative_path VARCHAR(4096) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_type VARCHAR(30) NOT NULL,
    size_bytes BIGINT NOT NULL,
    encoding VARCHAR(80),
    last_modified_at TIMESTAMP WITH TIME ZONE,
    scan_status VARCHAR(30) NOT NULL,
    parse_status VARCHAR(30) NOT NULL,
    parse_error VARCHAR(4000),
    framework_api_count INTEGER NOT NULL DEFAULT 0,
    generated BOOLEAN NOT NULL DEFAULT FALSE,
    sha256 VARCHAR(64),
    change_type VARCHAR(20) NOT NULL,
    structure_role VARCHAR(40),
    UNIQUE (scan_id, relative_path)
);

CREATE INDEX idx_project_file_scan_path ON project_file(scan_id, relative_path);
CREATE INDEX idx_project_file_project_type ON project_file(project_id, file_type);
CREATE INDEX idx_project_file_module ON project_file(module_id);

CREATE TABLE analysis_finding (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    module_id UUID REFERENCES project_module(id) ON DELETE SET NULL,
    file_id UUID REFERENCES project_file(id) ON DELETE CASCADE,
    file_path VARCHAR(4096) NOT NULL,
    line_start INTEGER,
    line_end INTEGER,
    analysis_kind VARCHAR(30) NOT NULL,
    object_key VARCHAR(160) NOT NULL,
    summary VARCHAR(1200) NOT NULL,
    rule_id VARCHAR(120) NOT NULL,
    evidence VARCHAR(4000) NOT NULL,
    certainty VARCHAR(30) NOT NULL,
    analyzer_version VARCHAR(40) NOT NULL,
    analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    parse_status VARCHAR(30) NOT NULL,
    metadata VARCHAR(4000)
);

CREATE INDEX idx_finding_scan_kind ON analysis_finding(scan_id, analysis_kind);
CREATE INDEX idx_finding_scan_file ON analysis_finding(scan_id, file_id);
CREATE INDEX idx_finding_scan_object ON analysis_finding(scan_id, object_key);
CREATE INDEX idx_finding_certainty ON analysis_finding(scan_id, certainty);

CREATE TABLE technology_usage (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES analysis_project(id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_run(id) ON DELETE CASCADE,
    technology_key VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    api_evidence_count INTEGER NOT NULL,
    configuration_evidence_count INTEGER NOT NULL,
    dependency_evidence_count INTEGER NOT NULL,
    total_evidence_count INTEGER NOT NULL,
    rule_ids VARCHAR(2000),
    first_evidence_path VARCHAR(4096),
    first_evidence_line INTEGER,
    analyzer_version VARCHAR(40) NOT NULL,
    analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (scan_id, technology_key)
);

CREATE INDEX idx_technology_scan ON technology_usage(scan_id, category);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80),
    detail VARCHAR(2000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_occurred ON audit_event(occurred_at DESC);
