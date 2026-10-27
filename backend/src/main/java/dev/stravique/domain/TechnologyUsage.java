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
@Table(name = "technology_usage")
public class TechnologyUsage {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "technology_key", nullable = false, length = 80)
    private String technologyKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TechnologyCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Certainty status;

    @Column(name = "api_evidence_count", nullable = false)
    private int apiEvidenceCount;

    @Column(name = "configuration_evidence_count", nullable = false)
    private int configurationEvidenceCount;

    @Column(name = "dependency_evidence_count", nullable = false)
    private int dependencyEvidenceCount;

    @Column(name = "total_evidence_count", nullable = false)
    private int totalEvidenceCount;

    @Column(name = "rule_ids", length = 2000)
    private String ruleIds;

    @Column(name = "first_evidence_path", length = 4096)
    private String firstEvidencePath;

    @Column(name = "first_evidence_line")
    private Integer firstEvidenceLine;

    @Column(name = "analyzer_version", nullable = false, length = 40)
    private String analyzerVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    public static TechnologyUsage create(UUID projectId, UUID scanId, String technologyKey, String name,
                                         TechnologyCategory category, Certainty status, int apiEvidenceCount,
                                         int configurationEvidenceCount, int dependencyEvidenceCount,
                                         String ruleIds, String firstEvidencePath, Integer firstEvidenceLine,
                                         String analyzerVersion) {
        TechnologyUsage usage = new TechnologyUsage();
        usage.id = UUID.randomUUID();
        usage.projectId = projectId;
        usage.scanId = scanId;
        usage.technologyKey = technologyKey;
        usage.name = name;
        usage.category = category;
        usage.status = status;
        usage.apiEvidenceCount = apiEvidenceCount;
        usage.configurationEvidenceCount = configurationEvidenceCount;
        usage.dependencyEvidenceCount = dependencyEvidenceCount;
        usage.totalEvidenceCount = apiEvidenceCount + configurationEvidenceCount + dependencyEvidenceCount;
        usage.ruleIds = ruleIds;
        usage.firstEvidencePath = firstEvidencePath;
        usage.firstEvidenceLine = firstEvidenceLine;
        usage.analyzerVersion = analyzerVersion;
        usage.analyzedAt = Instant.now();
        return usage;
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

    public String getTechnologyKey() {
        return technologyKey;
    }

    public String getName() {
        return name;
    }

    public TechnologyCategory getCategory() {
        return category;
    }

    public Certainty getStatus() {
        return status;
    }

    public int getApiEvidenceCount() {
        return apiEvidenceCount;
    }

    public int getConfigurationEvidenceCount() {
        return configurationEvidenceCount;
    }

    public int getDependencyEvidenceCount() {
        return dependencyEvidenceCount;
    }

    public int getTotalEvidenceCount() {
        return totalEvidenceCount;
    }

    public String getRuleIds() {
        return ruleIds;
    }

    public String getFirstEvidencePath() {
        return firstEvidencePath;
    }

    public Integer getFirstEvidenceLine() {
        return firstEvidenceLine;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
