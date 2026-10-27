package dev.stravique.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "project_module")
public class ProjectModule {

    @Id
    private UUID id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "relative_path", nullable = false, length = 2048)
    private String relativePath;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "source_roots", nullable = false, length = 4000)
    private String sourceRoots = "";

    @Column(name = "test_roots", nullable = false, length = 4000)
    private String testRoots = "";

    @Column(name = "resource_roots", nullable = false, length = 4000)
    private String resourceRoots = "";

    @Column(name = "web_roots", nullable = false, length = 4000)
    private String webRoots = "";

    @Column(name = "config_roots", nullable = false, length = 4000)
    private String configRoots = "";

    @Column(name = "sql_roots", nullable = false, length = 4000)
    private String sqlRoots = "";

    public static ProjectModule create(UUID scanId, UUID projectId, String relativePath, String name) {
        ProjectModule module = new ProjectModule();
        module.id = UUID.randomUUID();
        module.scanId = scanId;
        module.projectId = projectId;
        module.relativePath = relativePath;
        module.name = name;
        return module;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScanId() {
        return scanId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getName() {
        return name;
    }

    public String getSourceRoots() {
        return sourceRoots;
    }

    public void setSourceRoots(String sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    public String getTestRoots() {
        return testRoots;
    }

    public void setTestRoots(String testRoots) {
        this.testRoots = testRoots;
    }

    public String getResourceRoots() {
        return resourceRoots;
    }

    public void setResourceRoots(String resourceRoots) {
        this.resourceRoots = resourceRoots;
    }

    public String getWebRoots() {
        return webRoots;
    }

    public void setWebRoots(String webRoots) {
        this.webRoots = webRoots;
    }

    public String getConfigRoots() {
        return configRoots;
    }

    public void setConfigRoots(String configRoots) {
        this.configRoots = configRoots;
    }

    public String getSqlRoots() {
        return sqlRoots;
    }

    public void setSqlRoots(String sqlRoots) {
        this.sqlRoots = sqlRoots;
    }
}
