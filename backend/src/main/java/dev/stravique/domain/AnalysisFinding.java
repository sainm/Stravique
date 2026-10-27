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
@Table(name = "analysis_finding")
public class AnalysisFinding {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "file_path", nullable = false, length = 4096)
    private String filePath;

    @Column(name = "line_start")
    private Integer lineStart;

    @Column(name = "line_end")
    private Integer lineEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_kind", nullable = false, length = 30)
    private AnalysisKind analysisKind;

    @Column(name = "object_key", nullable = false, length = 160)
    private String objectKey;

    @Column(nullable = false, length = 1200)
    private String summary;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    @Column(nullable = false, length = 4000)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Certainty certainty;

    @Column(name = "analyzer_version", nullable = false, length = 40)
    private String analyzerVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 30)
    private ParseStatus parseStatus;

    @Column(length = 4000)
    private String metadata;

    public static AnalysisFinding create(UUID projectId, UUID scanId, UUID moduleId, UUID fileId,
                                         String filePath, Integer lineStart, Integer lineEnd,
                                         AnalysisKind kind, String objectKey, String summary, String ruleId,
                                         String evidence, Certainty certainty, String analyzerVersion,
                                         ParseStatus parseStatus, String metadata) {
        AnalysisFinding finding = new AnalysisFinding();
        finding.id = UUID.randomUUID();
        finding.projectId = projectId;
        finding.scanId = scanId;
        finding.moduleId = moduleId;
        finding.fileId = fileId;
        finding.filePath = filePath;
        finding.lineStart = lineStart;
        finding.lineEnd = lineEnd;
        finding.analysisKind = kind;
        finding.objectKey = objectKey;
        finding.summary = clip(summary, 1200);
        finding.ruleId = ruleId;
        finding.evidence = clip(evidence, 4000);
        finding.certainty = certainty;
        finding.analyzerVersion = analyzerVersion;
        finding.analyzedAt = Instant.now();
        finding.parseStatus = parseStatus;
        finding.metadata = metadata;
        return finding;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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

    public UUID getModuleId() {
        return moduleId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineStart() {
        return lineStart;
    }

    public Integer getLineEnd() {
        return lineEnd;
    }

    public AnalysisKind getAnalysisKind() {
        return analysisKind;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getSummary() {
        return summary;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getEvidence() {
        return evidence;
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

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getMetadata() {
        return metadata;
    }
}
