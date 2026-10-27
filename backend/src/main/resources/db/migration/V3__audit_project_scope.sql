ALTER TABLE audit_event
    ADD COLUMN project_id UUID REFERENCES analysis_project(id) ON DELETE CASCADE;

CREATE INDEX idx_audit_project_occurred
    ON audit_event(project_id, occurred_at DESC);
