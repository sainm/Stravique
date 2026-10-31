ALTER TABLE transformation_proposal
    ADD COLUMN target_sha256 VARCHAR(64);

UPDATE transformation_proposal
SET target_sha256 = CASE
    WHEN artifact_type = 'EDIT_FILE' THEN original_sha256
    ELSE NULL
END;

ALTER TABLE transformation_run
    ADD COLUMN target_sha256 VARCHAR(64);

UPDATE transformation_run
SET target_sha256 = CASE
    WHEN artifact_type = 'EDIT_FILE' THEN original_sha256
    ELSE NULL
END;
