package dev.stravique.api.dto;

import dev.stravique.domain.PathOverride;

import java.util.UUID;

public record PathOverrideDto(UUID id, String relativePath, String role) {

    public static PathOverrideDto from(PathOverride override) {
        return new PathOverrideDto(override.getId(), override.getRelativePath(), override.getRole().name());
    }
}
