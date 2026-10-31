package dev.stravique.api.dto;

public record TransformationBackupPruneDto(
        int requested,
        int deleted,
        int failed,
        long deletedBytes
) {
}
