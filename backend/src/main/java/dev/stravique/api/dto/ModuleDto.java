package dev.stravique.api.dto;

import dev.stravique.domain.ProjectModule;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ModuleDto(
        UUID id,
        String relativePath,
        String name,
        List<String> sourceRoots,
        List<String> testRoots,
        List<String> resourceRoots,
        List<String> webRoots,
        List<String> configRoots,
        List<String> sqlRoots
) {
    public static ModuleDto from(ProjectModule module) {
        return new ModuleDto(
                module.getId(),
                module.getRelativePath(),
                module.getName(),
                lines(module.getSourceRoots()),
                lines(module.getTestRoots()),
                lines(module.getResourceRoots()),
                lines(module.getWebRoots()),
                lines(module.getConfigRoots()),
                lines(module.getSqlRoots())
        );
    }

    private static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R")).map(String::trim)
                .filter(item -> !item.isEmpty()).toList();
    }
}
