package dev.stravique.api.dto;

import dev.stravique.application.ScanComparison;

public record ScanComparisonFileDto(
        String fileType,
        long sizeBytes,
        String sha256,
        String encoding,
        String scanStatus,
        String parseStatus,
        String structureRole,
        boolean generated
) {
    public static ScanComparisonFileDto from(ScanComparison.FileView file) {
        if (file == null) {
            return null;
        }
        return new ScanComparisonFileDto(
                file.fileType(),
                file.sizeBytes(),
                file.sha256(),
                file.encoding(),
                file.scanStatus(),
                file.parseStatus(),
                file.structureRole(),
                file.generated()
        );
    }
}
