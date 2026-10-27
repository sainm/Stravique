package dev.stravique.api.dto;

import dev.stravique.domain.AnalysisFinding;

import java.time.Instant;
import java.util.UUID;

public record FindingDto(
        UUID id,
        UUID scanId,
        UUID fileId,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String analysisKind,
        String objectKey,
        String summary,
        String ruleId,
        String evidence,
        String certainty,
        String analyzerVersion,
        Instant analyzedAt,
        String parseStatus,
        String metadata
) {
    public static FindingDto from(AnalysisFinding finding) {
        return new FindingDto(
                finding.getId(),
                finding.getScanId(),
                finding.getFileId(),
                finding.getFilePath(),
                finding.getLineStart(),
                finding.getLineEnd(),
                finding.getAnalysisKind().name(),
                finding.getObjectKey(),
                finding.getSummary(),
                finding.getRuleId(),
                finding.getEvidence(),
                finding.getCertainty().name(),
                finding.getAnalyzerVersion(),
                finding.getAnalyzedAt(),
                finding.getParseStatus().name(),
                finding.getMetadata()
        );
    }
}
