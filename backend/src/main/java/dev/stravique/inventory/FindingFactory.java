package dev.stravique.inventory;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.ParseStatus;
import org.springframework.stereotype.Component;

@Component
public class FindingFactory {

    public AnalysisFinding create(AnalyzerContext context, AnalysisKind kind, String objectKey,
                                  String summary, String ruleId, int line, Certainty certainty,
                                  ParseStatus parseStatus, String metadata) {
        String evidence = line(context.text(), line);
        return AnalysisFinding.create(
                context.projectId(),
                context.scanId(),
                context.moduleId(),
                context.fileId(),
                context.relativePath(),
                line <= 0 ? null : line,
                line <= 0 ? null : line,
                kind,
                objectKey,
                summary,
                ruleId,
                evidence,
                certainty,
                context.analyzerVersion(),
                parseStatus,
                metadata
        );
    }

    public AnalysisFinding parseFailure(AnalyzerContext context, String ruleId, String exceptionType,
                                        String message) {
        return AnalysisFinding.create(
                context.projectId(),
                context.scanId(),
                context.moduleId(),
                context.fileId(),
                context.relativePath(),
                null,
                null,
                AnalysisKind.PARSE_ERROR,
                "PARSE_FAILURE",
                "The configured parser could not analyze this file.",
                ruleId,
                exceptionType + ": " + safeMessage(message),
                Certainty.PARSE_FAILED,
                context.analyzerVersion(),
                ParseStatus.FAILED,
                null
        );
    }

    public String line(String text, int lineNumber) {
        if (lineNumber <= 0) {
            return "";
        }
        String[] lines = text.split("\\R", -1);
        if (lineNumber > lines.length) {
            return "";
        }
        String value = lines[lineNumber - 1].strip();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        String singleLine = message.replaceAll("\\s+", " ");
        if (singleLine.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
            return "Parser rejected the input; verify syntax and supported features.";
        }
        return singleLine.length() <= 1000 ? singleLine : singleLine.substring(0, 1000);
    }
}
