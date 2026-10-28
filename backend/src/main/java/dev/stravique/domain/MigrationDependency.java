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
@Table(name = "migration_dependency")
public class MigrationDependency {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "from_item_id", nullable = false)
    private UUID fromItemId;

    @Column(name = "to_item_id", nullable = false)
    private UUID toItemId;

    @Column(name = "relation_id")
    private UUID relationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 40)
    private AnalysisRelationType relationType;

    @Column(name = "evidence_path", nullable = false, length = 4096)
    private String evidencePath;

    @Column(name = "line_start")
    private Integer lineStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Certainty certainty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static MigrationDependency create(UUID planId, UUID projectId, UUID scanId,
                                             UUID fromItemId, UUID toItemId, UUID relationId,
                                             AnalysisRelationType relationType, String evidencePath,
                                             Integer lineStart, Certainty certainty) {
        MigrationDependency dependency = new MigrationDependency();
        dependency.id = UUID.randomUUID();
        dependency.planId = planId;
        dependency.projectId = projectId;
        dependency.scanId = scanId;
        dependency.fromItemId = fromItemId;
        dependency.toItemId = toItemId;
        dependency.relationId = relationId;
        dependency.relationType = relationType;
        dependency.evidencePath = evidencePath;
        dependency.lineStart = lineStart;
        dependency.certainty = certainty;
        dependency.createdAt = Instant.now();
        return dependency;
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

    public UUID getFromItemId() {
        return fromItemId;
    }

    public UUID getToItemId() {
        return toItemId;
    }

    public UUID getRelationId() {
        return relationId;
    }

    public AnalysisRelationType getRelationType() {
        return relationType;
    }

    public String getEvidencePath() {
        return evidencePath;
    }

    public Integer getLineStart() {
        return lineStart;
    }

    public Certainty getCertainty() {
        return certainty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
