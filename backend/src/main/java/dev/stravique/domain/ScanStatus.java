package dev.stravique.domain;

public enum ScanStatus {
    QUEUED,
    RUNNING,
    CANCEL_REQUESTED,
    CANCELLED,
    COMPLETED,
    FAILED
}
