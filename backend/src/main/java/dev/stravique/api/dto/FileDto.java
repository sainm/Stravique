package dev.stravique.api.dto;

import dev.stravique.domain.ProjectFile;

import java.time.Instant;
import java.util.UUID;

public record FileDto(
        UUID id,
        UUID scanId,
        UUID moduleId,
        String relativePath,
        String fileName,
        String fileType,
        long sizeBytes,
        String encoding,
        Instant lastModifiedAt,
        String scanStatus,
        String parseStatus,
        String parseError,
        int frameworkApiCount,
        boolean generated,
        String changeType,
        String structureRole
) {
    public static FileDto from(ProjectFile file) {
        return new FileDto(
                file.getId(),
                file.getScanId(),
                file.getModuleId(),
                file.getRelativePath(),
                file.getFileName(),
                file.getFileType().name(),
                file.getSizeBytes(),
                file.getEncoding(),
                file.getLastModifiedAt(),
                file.getScanStatus().name(),
                file.getParseStatus().name(),
                file.getParseError(),
                file.getFrameworkApiCount(),
                file.isGenerated(),
                file.getChangeType().name(),
                file.getStructureRole()
        );
    }
}
