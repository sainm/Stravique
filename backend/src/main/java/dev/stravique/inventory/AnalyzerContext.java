package dev.stravique.inventory;

import dev.stravique.domain.FileType;

import java.util.UUID;

public record AnalyzerContext(
        UUID projectId,
        UUID scanId,
        UUID moduleId,
        UUID fileId,
        String relativePath,
        FileType fileType,
        String text,
        String analyzerVersion,
        TechEvidenceLedger ledger
) {
}
