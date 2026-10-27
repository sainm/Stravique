package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record InventoryTreeDto(
        UUID projectId,
        UUID scanId,
        List<ModuleDto> modules,
        List<ModuleDependencyDto> moduleDependencies,
        List<DirectoryNodeDto> directories
) {
}
