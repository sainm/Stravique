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
@Table(name = "migration_item")
public class MigrationItem {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "item_key", nullable = false, length = 512)
    private String itemKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 40)
    private MigrationItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_key", nullable = false, length = 40)
    private MigrationTarget targetKey;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "primary_file_id")
    private UUID primaryFileId;

    @Column(name = "primary_finding_id")
    private UUID primaryFindingId;

    @Column(nullable = false)
    private int phase;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_level", nullable = false, length = 20)
    private MigrationAutomation automationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private MigrationRisk riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MigrationItemStatus status;

    @Column(nullable = false)
    private boolean included;

    @Column(name = "reason_code", nullable = false, length = 60)
    private String reasonCode;

    @Column(name = "finding_count", nullable = false)
    private int findingCount;

    @Column(name = "dependency_count", nullable = false)
    private int dependencyCount;

    @Column(name = "dependent_count", nullable = false)
    private int dependentCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static MigrationItem create(UUID planId, UUID projectId, UUID scanId, String itemKey,
                                       MigrationItemType itemType, MigrationTarget targetKey,
                                       String title, UUID primaryFileId, UUID primaryFindingId,
                                       int priority, MigrationAutomation automationLevel,
                                       MigrationRisk riskLevel, MigrationItemStatus status,
                                       String reasonCode) {
        MigrationItem item = new MigrationItem();
        item.id = UUID.randomUUID();
        item.planId = planId;
        item.projectId = projectId;
        item.scanId = scanId;
        item.itemKey = itemKey;
        item.itemType = itemType;
        item.targetKey = targetKey;
        item.title = title;
        item.primaryFileId = primaryFileId;
        item.primaryFindingId = primaryFindingId;
        item.phase = 1;
        item.priority = priority;
        item.automationLevel = automationLevel;
        item.riskLevel = riskLevel;
        item.status = status;
        item.included = true;
        item.reasonCode = reasonCode;
        item.createdAt = Instant.now();
        return item;
    }

    public void updateGraphCounts(int findings, int dependencies, int dependents) {
        this.findingCount = findings;
        this.dependencyCount = dependencies;
        this.dependentCount = dependents;
    }

    public void placeInPhase(int phase) {
        this.phase = phase;
    }

    public void requireReview(String reasonCode) {
        this.status = MigrationItemStatus.REVIEW_REQUIRED;
        this.riskLevel = MigrationRisk.HIGH;
        this.reasonCode = reasonCode;
    }

    public void setIncluded(boolean included) {
        this.included = included;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getScanId() {
        return scanId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public MigrationItemType getItemType() {
        return itemType;
    }

    public MigrationTarget getTargetKey() {
        return targetKey;
    }

    public String getTitle() {
        return title;
    }

    public UUID getPrimaryFileId() {
        return primaryFileId;
    }

    public UUID getPrimaryFindingId() {
        return primaryFindingId;
    }

    public int getPhase() {
        return phase;
    }

    public int getPriority() {
        return priority;
    }

    public MigrationAutomation getAutomationLevel() {
        return automationLevel;
    }

    public MigrationRisk getRiskLevel() {
        return riskLevel;
    }

    public MigrationItemStatus getStatus() {
        return status;
    }

    public boolean isIncluded() {
        return included;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public int getDependencyCount() {
        return dependencyCount;
    }

    public int getDependentCount() {
        return dependentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
