package dev.stravique.api.dto;

import java.util.List;

public record DirectoryNodeDto(
        String name,
        String path,
        long fileCount,
        List<DirectoryNodeDto> children
) {
}
