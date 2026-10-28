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
@Table(name = "migration_plan")
public class MigrationPlan {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "plan_sequence", nullable = false)
    private int sequence;

    @Column(name = "source_profile", nullable = false, length = 200)
    private String sourceProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_profile", nullable = false, length = 40)
    private MigrationTargetProfile targetProfile;

    @Column(name = "source_scan_sequence", nullable = false)
    private int sourceScanSequence;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "included_count", nullable = false)
    private int includedCount;

    @Column(name = "ready_count", nullable = false)
    private int readyCount;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "blocked_count", nullable = false)
    private int blockedCount;

    @Column(name = "dependency_count", nullable = false)
    private int dependencyCount;

    @Column(name = "phase_count", nullable = false)
    private int phaseCount;

    @Column(name = "analyzer_version", nullable = false, length = 40)
    private String analyzerVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MigrationPlan create(UUID projectId, UUID scanId, int sequence, String sourceProfile,
                                       MigrationTargetProfile targetProfile, int sourceScanSequence,
                                       String analyzerVersion) {
        MigrationPlan plan = new MigrationPlan();
        plan.id = UUID.randomUUID();
        plan.projectId = projectId;
        plan.scanId = scanId;
        plan.sequence = sequence;
        plan.sourceProfile = sourceProfile;
        plan.targetProfile = targetProfile;
        plan.sourceScanSequence = sourceScanSequence;
        plan.analyzerVersion = analyzerVersion;
        plan.createdAt = Instant.now();
        plan.updatedAt = plan.createdAt;
        return plan;
    }

    public void updateCounts(int items, int included, int ready, int review, int blocked,
                             int dependencies, int phases) {
        this.itemCount = items;
        this.includedCount = included;
        this.readyCount = ready;
        this.reviewCount = review;
        this.blockedCount = blocked;
        this.dependencyCount = dependencies;
        this.phaseCount = phases;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getScanId() {
        return scanId;
    }

    public int getSequence() {
        return sequence;
    }

    public String getSourceProfile() {
        return sourceProfile;
    }

    public MigrationTargetProfile getTargetProfile() {
        return targetProfile;
    }

    public int getSourceScanSequence() {
        return sourceScanSequence;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getIncludedCount() {
        return includedCount;
    }

    public int getReadyCount() {
        return readyCount;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public int getDependencyCount() {
        return dependencyCount;
    }

    public int getPhaseCount() {
        return phaseCount;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
