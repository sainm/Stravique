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
@Table(name = "analysis_relation")
public class AnalysisRelation {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "source_file_id", nullable = false)
    private UUID sourceFileId;

    @Column(name = "target_file_id", nullable = false)
    private UUID targetFileId;

    @Column(name = "source_object_key", nullable = false, length = 512)
    private String sourceObjectKey;

    @Column(name = "target_object_key", nullable = false, length = 512)
    private String targetObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 40)
    private AnalysisRelationType relationType;

    @Column(name = "evidence_path", nullable = false, length = 4096)
    private String evidencePath;

    @Column(name = "line_start")
    private Integer lineStart;

    @Column(nullable = false, length = 4000)
    private String evidence;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Certainty certainty;

    @Column(name = "analyzer_version", nullable = false, length = 40)
    private String analyzerVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    public static AnalysisRelation create(UUID projectId, UUID scanId, UUID sourceFileId,
                                          UUID targetFileId, String sourceObjectKey,
                                          String targetObjectKey, AnalysisRelationType relationType,
                                          String evidencePath, Integer lineStart, String evidence,
                                          String ruleId, Certainty certainty, String analyzerVersion) {
        AnalysisRelation relation = new AnalysisRelation();
        relation.id = UUID.randomUUID();
        relation.projectId = projectId;
        relation.scanId = scanId;
        relation.sourceFileId = sourceFileId;
        relation.targetFileId = targetFileId;
        relation.sourceObjectKey = sourceObjectKey;
        relation.targetObjectKey = targetObjectKey;
        relation.relationType = relationType;
        relation.evidencePath = evidencePath;
        relation.lineStart = lineStart;
        relation.evidence = evidence;
        relation.ruleId = ruleId;
        relation.certainty = certainty;
        relation.analyzerVersion = analyzerVersion;
        relation.analyzedAt = Instant.now();
        return relation;
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

    public UUID getSourceFileId() {
        return sourceFileId;
    }

    public UUID getTargetFileId() {
        return targetFileId;
    }

    public String getSourceObjectKey() {
        return sourceObjectKey;
    }

    public String getTargetObjectKey() {
        return targetObjectKey;
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

    public String getEvidence() {
        return evidence;
    }

    public String getRuleId() {
        return ruleId;
    }

    public Certainty getCertainty() {
        return certainty;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
