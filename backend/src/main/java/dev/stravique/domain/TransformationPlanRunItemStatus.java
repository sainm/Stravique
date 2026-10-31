package dev.stravique.domain;

public enum TransformationPlanRunItemStatus {
    APPLIED,
    FAILED,
    ROLLED_BACK,
    ROLLBACK_FAILED,
    NOT_ATTEMPTED,
    RESTORED
}
