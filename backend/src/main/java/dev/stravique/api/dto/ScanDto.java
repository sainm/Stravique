package dev.stravique.api.dto;

import dev.stravique.domain.ScanRun;

import java.time.Instant;

public record ScanDto(
        java.util.UUID id,
        int sequence,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        int fileCount,
        int moduleCount,
        int analyzedCount,
        int parseFailureCount,
        int skippedCount,
        int findingCount,
        int reviewCount,
        int technologyCount,
        int removedCount,
        String errorCode,
        String errorMessage,
        String analyzerVersion
) {
    public static ScanDto from(ScanRun scan) {
        return new ScanDto(
                scan.getId(),
                scan.getSequence(),
                scan.getStatus().name(),
                scan.getCreatedAt(),
                scan.getStartedAt(),
                scan.getFinishedAt(),
                scan.getFileCount(),
                scan.getModuleCount(),
                scan.getAnalyzedCount(),
                scan.getParseFailureCount(),
                scan.getSkippedCount(),
                scan.getFindingCount(),
                scan.getReviewCount(),
                scan.getTechnologyCount(),
                scan.getRemovedCount(),
                scan.getErrorCode(),
                scan.getErrorMessage(),
                scan.getAnalyzerVersion()
        );
    }
}
