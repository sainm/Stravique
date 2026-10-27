package dev.stravique.inventory;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.ParseStatus;

import java.util.List;

public record AnalysisResult(
        List<AnalysisFinding> findings,
        ParseStatus parseStatus,
        String parseError
) {
    public static AnalysisResult empty(ParseStatus status) {
        return new AnalysisResult(List.of(), status, null);
    }

    public long frameworkApiCount() {
        return findings.stream()
                .filter(finding -> finding.getAnalysisKind() == dev.stravique.domain.AnalysisKind.FRAMEWORK_API)
                .count();
    }
}
