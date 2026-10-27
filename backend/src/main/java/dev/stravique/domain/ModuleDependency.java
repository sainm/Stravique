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
@Table(name = "module_dependency")
public class ModuleDependency {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "from_module_id", nullable = false)
    private UUID fromModuleId;

    @Column(name = "to_module_id", nullable = false)
    private UUID toModuleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 30)
    private ModuleDependencyType dependencyType;

    @Column(name = "evidence_path", nullable = false, length = 4096)
    private String evidencePath;

    @Column(name = "line_start")
    private Integer lineStart;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Certainty certainty;

    @Column(name = "analyzer_version", nullable = false, length = 40)
    private String analyzerVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    public static ModuleDependency create(UUID projectId, UUID scanId, UUID fromModuleId,
                                          UUID toModuleId, ModuleDependencyType type,
                                          String evidencePath, Integer lineStart, String ruleId,
                                          Certainty certainty, String analyzerVersion) {
        ModuleDependency dependency = new ModuleDependency();
        dependency.id = UUID.randomUUID();
        dependency.projectId = projectId;
        dependency.scanId = scanId;
        dependency.fromModuleId = fromModuleId;
        dependency.toModuleId = toModuleId;
        dependency.dependencyType = type;
        dependency.evidencePath = evidencePath;
        dependency.lineStart = lineStart;
        dependency.ruleId = ruleId;
        dependency.certainty = certainty;
        dependency.analyzerVersion = analyzerVersion;
        dependency.analyzedAt = Instant.now();
        return dependency;
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

    public UUID getFromModuleId() {
        return fromModuleId;
    }

    public UUID getToModuleId() {
        return toModuleId;
    }

    public ModuleDependencyType getDependencyType() {
        return dependencyType;
    }

    public String getEvidencePath() {
        return evidencePath;
    }

    public Integer getLineStart() {
        return lineStart;
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
