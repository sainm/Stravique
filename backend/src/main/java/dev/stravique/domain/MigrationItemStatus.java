package dev.stravique.domain;

public enum MigrationItemStatus {
    READY,
    REVIEW_REQUIRED,
    BLOCKED,
    TRANSFORMED,
    VALIDATED,
    VALIDATION_FAILED
}
