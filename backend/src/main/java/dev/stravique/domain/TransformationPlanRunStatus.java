package dev.stravique.domain;

public enum TransformationPlanRunStatus {
    RUNNING,
    INTERRUPTED,
    CANCELLED,
    APPLIED,
    FAILED,
    ROLLBACK_FAILED,
    RESTORED,
    RECOVERED,
    PARTIAL
}
