package dev.stravique.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_project")
public class AnalysisProject {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description = "";

    @Column(name = "source_path", nullable = false, length = 4096)
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 40)
    private ProjectType projectType = ProjectType.UNKNOWN;

    @Column(name = "jdk_version", length = 32)
    private String jdkVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_tool", nullable = false, length = 20)
    private BuildTool buildTool = BuildTool.UNKNOWN;

    @Column(name = "source_framework", length = 160)
    private String sourceFramework;

    @Column(name = "target_framework", length = 160)
    private String targetFramework;

    @Column(name = "excluded_directories", nullable = false, length = 4000)
    private String excludedDirectories = "";

    @Column(name = "excluded_files", nullable = false, length = 4000)
    private String excludedFiles = "";

    @Column(name = "analysis_scope", nullable = false, length = 40)
    private String analysisScope = "FULL";

    @Column(name = "last_scan_at")
    private Instant lastScanAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static AnalysisProject create(String name, String description, String sourcePath) {
        Instant now = Instant.now();
        AnalysisProject project = new AnalysisProject();
        project.id = UUID.randomUUID();
        project.name = name;
        project.description = description == null ? "" : description;
        project.sourcePath = sourcePath;
        project.createdAt = now;
        project.updatedAt = now;
        return project;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public ProjectType getProjectType() {
        return projectType;
    }

    public void setProjectType(ProjectType projectType) {
        this.projectType = projectType;
    }

    public String getJdkVersion() {
        return jdkVersion;
    }

    public void setJdkVersion(String jdkVersion) {
        this.jdkVersion = jdkVersion;
    }

    public BuildTool getBuildTool() {
        return buildTool;
    }

    public void setBuildTool(BuildTool buildTool) {
        this.buildTool = buildTool;
    }

    public String getSourceFramework() {
        return sourceFramework;
    }

    public void setSourceFramework(String sourceFramework) {
        this.sourceFramework = sourceFramework;
    }

    public String getTargetFramework() {
        return targetFramework;
    }

    public void setTargetFramework(String targetFramework) {
        this.targetFramework = targetFramework;
    }

    public String getExcludedDirectories() {
        return excludedDirectories;
    }

    public void setExcludedDirectories(String excludedDirectories) {
        this.excludedDirectories = excludedDirectories;
    }

    public String getExcludedFiles() {
        return excludedFiles;
    }

    public void setExcludedFiles(String excludedFiles) {
        this.excludedFiles = excludedFiles;
    }

    public String getAnalysisScope() {
        return analysisScope;
    }

    public void setAnalysisScope(String analysisScope) {
        this.analysisScope = analysisScope;
    }

    public Instant getLastScanAt() {
        return lastScanAt;
    }

    public void setLastScanAt(Instant lastScanAt) {
        this.lastScanAt = lastScanAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public long getVersion() {
        return version;
    }
}
