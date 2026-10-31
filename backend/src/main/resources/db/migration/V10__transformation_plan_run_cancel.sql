ALTER TABLE transformation_plan_run
    ADD COLUMN cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;
