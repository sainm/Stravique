package dev.stravique.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_run")
public class ScanRun {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_sequence", nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScanStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "module_count", nullable = false)
    private int moduleCount;

    @Column(name = "analyzed_count", nullable = false)
    private int analyzedCount;

    @Column(name = "parse_failure_count", nullable = false)
    private int parseFailureCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "finding_count", nullable = false)
    private int findingCount;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "technology_count", nullable = false)
    private int technologyCount;

    @Column(name = "removed_count", nullable = false)
    private int removedCount;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "analyzer_version", nullable = false, length = 40)
    private String analyzerVersion;

    public static ScanRun queued(UUID projectId, int sequence, String analyzerVersion) {
        ScanRun run = new ScanRun();
        run.id = UUID.randomUUID();
        run.projectId = projectId;
        run.sequence = sequence;
        run.status = ScanStatus.QUEUED;
        run.createdAt = Instant.now();
        run.analyzerVersion = analyzerVersion;
        return run;
    }

    public void start() {
        this.status = ScanStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete() {
        this.status = ScanStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void fail(String code, String message) {
        this.status = ScanStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
        this.finishedAt = Instant.now();
    }

    public void requestCancel() {
        if (status == ScanStatus.QUEUED || status == ScanStatus.RUNNING) {
            this.status = ScanStatus.CANCEL_REQUESTED;
        }
    }

    public void cancel() {
        this.status = ScanStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getSequence() {
        return sequence;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getFileCount() {
        return fileCount;
    }

    public int getModuleCount() {
        return moduleCount;
    }

    public int getAnalyzedCount() {
        return analyzedCount;
    }

    public int getParseFailureCount() {
        return parseFailureCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public int getTechnologyCount() {
        return technologyCount;
    }

    public int getRemovedCount() {
        return removedCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public void updateCounts(int files, int modules, int analyzed, int parseFailures, int skipped,
                             int findings, int reviews, int technologies, int removed) {
        this.fileCount = files;
        this.moduleCount = modules;
        this.analyzedCount = analyzed;
        this.parseFailureCount = parseFailures;
        this.skippedCount = skipped;
        this.findingCount = findings;
        this.reviewCount = reviews;
        this.technologyCount = technologies;
        this.removedCount = removed;
    }
}
