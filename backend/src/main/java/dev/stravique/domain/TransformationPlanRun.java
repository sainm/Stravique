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
@Table(name = "transformation_plan_run")
public class TransformationPlanRun {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransformationPlanRunStatus status;

    @Column(name = "eligible_count", nullable = false)
    private int eligibleCount;

    @Column(name = "applied_count", nullable = false)
    private int appliedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "rolled_back_count", nullable = false)
    private int rolledBackCount;

    @Column(name = "restored_count", nullable = false)
    private int restoredCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    public static TransformationPlanRun started(UUID projectId, UUID planId, int eligibleCount) {
        TransformationPlanRun run = new TransformationPlanRun();
        run.id = UUID.randomUUID();
        run.projectId = projectId;
        run.planId = planId;
        run.status = TransformationPlanRunStatus.RUNNING;
        run.eligibleCount = eligibleCount;
        run.createdAt = Instant.now();
        return run;
    }

    public void markApplied(int applied) {
        this.status = TransformationPlanRunStatus.APPLIED;
        this.appliedCount = applied;
        this.completedAt = Instant.now();
    }

    public void markFailed(int applied, int rolledBack, boolean rollbackFailed) {
        this.status = rollbackFailed
                ? TransformationPlanRunStatus.ROLLBACK_FAILED
                : TransformationPlanRunStatus.FAILED;
        this.appliedCount = applied;
        this.rolledBackCount = rolledBack;
        this.failedCount = 1;
        this.completedAt = Instant.now();
    }

    public void markRestored(int restored, int failed, boolean rollbackFailed) {
        this.status = rollbackFailed
                ? TransformationPlanRunStatus.ROLLBACK_FAILED
                : failed == 0 ? TransformationPlanRunStatus.RESTORED
                : TransformationPlanRunStatus.PARTIAL;
        this.restoredCount = restored;
        this.failedCount = failed;
        this.completedAt = Instant.now();
    }

    public void markRecovered(int applied, int restored, int failed, boolean rollbackFailed) {
        this.status = rollbackFailed
                ? TransformationPlanRunStatus.ROLLBACK_FAILED
                : failed == 0 ? TransformationPlanRunStatus.RECOVERED
                : TransformationPlanRunStatus.PARTIAL;
        this.appliedCount = applied;
        this.restoredCount = restored;
        this.failedCount = failed;
        this.completedAt = Instant.now();
    }

    public void markInterrupted() {
        this.status = TransformationPlanRunStatus.INTERRUPTED;
        this.completedAt = Instant.now();
    }

    public void resumeFromInterrupted() {
        this.status = TransformationPlanRunStatus.RUNNING;
        this.cancelRequested = false;
        this.completedAt = null;
    }

    public void markCancelled(int applied, int rolledBack, boolean rollbackFailed) {
        this.status = rollbackFailed
                ? TransformationPlanRunStatus.ROLLBACK_FAILED
                : TransformationPlanRunStatus.CANCELLED;
        this.appliedCount = applied;
        this.rolledBackCount = rolledBack;
        this.completedAt = Instant.now();
    }

    public void requestCancel() {
        this.cancelRequested = true;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public TransformationPlanRunStatus getStatus() {
        return status;
    }

    public int getEligibleCount() {
        return eligibleCount;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getRolledBackCount() {
        return rolledBackCount;
    }

    public int getRestoredCount() {
        return restoredCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
