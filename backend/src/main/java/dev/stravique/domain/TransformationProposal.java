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
@Table(name = "transformation_proposal")
public class TransformationProposal {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "source_file_id", nullable = false)
    private UUID sourceFileId;

    @Column(name = "relative_path", nullable = false, length = 4096)
    private String relativePath;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, length = 40)
    private String ruleVersion;

    @Column(name = "original_sha256", length = 64)
    private String originalSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 20)
    private TransformationArtifactType artifactType = TransformationArtifactType.EDIT_FILE;

    @Column(name = "proposed_sha256", nullable = false, length = 64)
    private String proposedSha256;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diff;

    @Column(name = "validation_status", nullable = false, length = 20)
    private String validationStatus;

    @Column(name = "validation_message", length = 4000)
    private String validationMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransformationProposalStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "restored_at")
    private Instant restoredAt;

    public static TransformationProposal create(UUID projectId, UUID planId, UUID itemId,
                                                UUID sourceFileId, String relativePath,
                                                String ruleId, String ruleVersion,
                                                String originalSha256, String proposedSha256,
                                                String diff, String validationStatus,
                                                String validationMessage,
                                                TransformationArtifactType artifactType) {
        TransformationProposal proposal = new TransformationProposal();
        proposal.id = UUID.randomUUID();
        proposal.projectId = projectId;
        proposal.planId = planId;
        proposal.itemId = itemId;
        proposal.sourceFileId = sourceFileId;
        proposal.relativePath = relativePath;
        proposal.ruleId = ruleId;
        proposal.ruleVersion = ruleVersion;
        proposal.originalSha256 = originalSha256;
        proposal.proposedSha256 = proposedSha256;
        proposal.artifactType = artifactType;
        proposal.diff = diff;
        proposal.validationStatus = validationStatus;
        proposal.validationMessage = validationMessage;
        proposal.status = TransformationProposalStatus.PROPOSED;
        proposal.createdAt = Instant.now();
        return proposal;
    }

    public void markApplied() {
        this.status = TransformationProposalStatus.APPLIED;
        this.appliedAt = Instant.now();
    }

    public void markRestored() {
        this.status = TransformationProposalStatus.RESTORED;
        this.restoredAt = Instant.now();
    }

    public void supersede() {
        if (status == TransformationProposalStatus.PROPOSED) {
            this.status = TransformationProposalStatus.SUPERSEDED;
        }
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

    public UUID getItemId() {
        return itemId;
    }

    public UUID getSourceFileId() {
        return sourceFileId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public String getOriginalSha256() {
        return originalSha256;
    }

    public TransformationArtifactType getArtifactType() {
        return artifactType;
    }

    public String getProposedSha256() {
        return proposedSha256;
    }

    public String getDiff() {
        return diff;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public TransformationProposalStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getRestoredAt() {
        return restoredAt;
    }
}
