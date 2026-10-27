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
@Table(name = "path_override")
public class PathOverride {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "relative_path", nullable = false, length = 2048)
    private String relativePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PathRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PathOverride create(UUID projectId, String relativePath, PathRole role) {
        PathOverride override = new PathOverride();
        override.id = UUID.randomUUID();
        override.projectId = projectId;
        override.relativePath = relativePath;
        override.role = role;
        override.createdAt = Instant.now();
        return override;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public PathRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
