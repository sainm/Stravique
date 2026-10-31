package dev.stravique.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "transformation_plan_run_item")
public class TransformationPlanRunItem {

    @Id
    private UUID id;

    @Column(name = "plan_run_id", nullable = false)
    private UUID planRunId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransformationPlanRunItemStatus status;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    public static TransformationPlanRunItem planned(UUID planRunId, UUID projectId, UUID planId,
                                                    UUID itemId, int ordinal) {
        TransformationPlanRunItem item = new TransformationPlanRunItem();
        item.id = UUID.randomUUID();
        item.planRunId = planRunId;
        item.projectId = projectId;
        item.planId = planId;
        item.itemId = itemId;
        item.ordinal = ordinal;
        item.status = TransformationPlanRunItemStatus.NOT_ATTEMPTED;
        return item;
    }

    public void markApplied(UUID runId) {
        this.status = TransformationPlanRunItemStatus.APPLIED;
        this.runId = runId;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = TransformationPlanRunItemStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public void markRolledBack() {
        this.status = TransformationPlanRunItemStatus.ROLLED_BACK;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markRollbackFailed(String errorCode, String errorMessage) {
        this.status = TransformationPlanRunItemStatus.ROLLBACK_FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public void markRestored() {
        this.status = TransformationPlanRunItemStatus.RESTORED;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanRunId() {
        return planRunId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public TransformationPlanRunItemStatus getStatus() {
        return status;
    }

    public UUID getRunId() {
        return runId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
