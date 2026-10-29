ALTER TABLE transformation_proposal
    ADD COLUMN artifact_type VARCHAR(20) NOT NULL DEFAULT 'EDIT_FILE';

ALTER TABLE transformation_proposal
    ALTER COLUMN original_sha256 DROP NOT NULL;

ALTER TABLE transformation_run
    ADD COLUMN artifact_type VARCHAR(20) NOT NULL DEFAULT 'EDIT_FILE';

ALTER TABLE transformation_run
    ALTER COLUMN original_sha256 DROP NOT NULL;