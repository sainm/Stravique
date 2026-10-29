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
@Table(name = "transformation_run")
public class TransformationRun {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransformationRunStatus status;

    @Column(name = "relative_path", nullable = false, length = 4096)
    private String relativePath;

    @Column(name = "backup_path", length = 4096)
    private String backupPath;

    @Column(name = "original_sha256", length = 64)
    private String originalSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 20)
    private TransformationArtifactType artifactType = TransformationArtifactType.EDIT_FILE;

    @Column(name = "applied_sha256", nullable = false, length = 64)
    private String appliedSha256;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, length = 40)
    private String ruleVersion;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public static TransformationRun applied(UUID projectId, UUID planId, UUID proposalId,
                                            UUID itemId, String relativePath, String backupPath,
                                            String originalSha256, String appliedSha256,
                                            String ruleId, String ruleVersion,
                                            TransformationArtifactType artifactType) {
        TransformationRun run = new TransformationRun();
        run.id = UUID.randomUUID();
        run.projectId = projectId;
        run.planId = planId;
        run.proposalId = proposalId;
        run.itemId = itemId;
        run.status = TransformationRunStatus.APPLIED;
        run.relativePath = relativePath;
        run.backupPath = backupPath;
        run.originalSha256 = originalSha256;
        run.appliedSha256 = appliedSha256;
        run.ruleId = ruleId;
        run.ruleVersion = ruleVersion;
        run.artifactType = artifactType;
        run.createdAt = Instant.now();
        run.finishedAt = run.createdAt;
        return run;
    }

    public void markRestored() {
        this.status = TransformationRunStatus.RESTORED;
        this.finishedAt = Instant.now();
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

    public UUID getProposalId() {
        return proposalId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public TransformationRunStatus getStatus() {
        return status;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public String getOriginalSha256() {
        return originalSha256;
    }

    public TransformationArtifactType getArtifactType() {
        return artifactType;
    }

    public String getAppliedSha256() {
        return appliedSha256;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
