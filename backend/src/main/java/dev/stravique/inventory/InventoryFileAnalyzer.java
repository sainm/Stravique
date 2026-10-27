package dev.stravique.inventory;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ParseStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class InventoryFileAnalyzer {

    private final JavaInventoryAnalyzer javaAnalyzer;
    private final ConfigurationInventoryAnalyzer configurationAnalyzer;
    private final WebInventoryAnalyzer webAnalyzer;
    private final FindingFactory findings;

    public InventoryFileAnalyzer(JavaInventoryAnalyzer javaAnalyzer,
                                 ConfigurationInventoryAnalyzer configurationAnalyzer,
                                 WebInventoryAnalyzer webAnalyzer, FindingFactory findings) {
        this.javaAnalyzer = javaAnalyzer;
        this.configurationAnalyzer = configurationAnalyzer;
        this.webAnalyzer = webAnalyzer;
        this.findings = findings;
    }

    public AnalysisResult analyze(AnalyzerContext context) {
        if (context.fileType() == FileType.JAVA || context.fileType() == FileType.TEST) {
            return javaAnalyzer.analyze(context);
        }
        if (context.fileType() == FileType.JSP) {
            return webAnalyzer.analyzeJsp(context);
        }
        if (context.fileType() == FileType.SQL) {
            return analyzeSql(context);
        }
        if (context.fileType() == FileType.HTML || context.fileType() == FileType.JAVASCRIPT
                || context.fileType() == FileType.CSS) {
            return analyzeStaticAsset(context);
        }
        return configurationAnalyzer.analyze(context);
    }

    private AnalysisResult analyzeSql(AnalyzerContext context) {
        String lower = context.text().toLowerCase(Locale.ROOT);
        if (!(lower.contains("select ") || lower.contains("insert ") || lower.contains("update ")
                || lower.contains("delete "))) {
            return AnalysisResult.empty(ParseStatus.PARTIAL);
        }
        context.ledger().recordApi("DB_ACCESS", "SQL / database access",
                context.relativePath(), 1, "SQL-STATEMENT-001");
        AnalysisFinding finding = findings.create(context, AnalysisKind.TECHNOLOGY,
                "DB_ACCESS", "SQL statement source identified.", "SQL-STATEMENT-001",
                1, Certainty.ANALYZED, ParseStatus.PARTIAL, null);
        return new AnalysisResult(List.of(finding), ParseStatus.PARTIAL, null);
    }

    private AnalysisResult analyzeStaticAsset(AnalyzerContext context) {
        List<AnalysisFinding> output = new ArrayList<>();
        String lower = context.text().toLowerCase(Locale.ROOT);
        if (lower.contains("<form") || lower.contains("fetch(") || lower.contains("axios.")) {
            output.add(findings.create(context, AnalysisKind.REVIEW_ITEM, "CLIENT_SERVER_CALL_CANDIDATE",
                    "Client-side request or form submission is a candidate only.",
                    "WEB-CLIENT-CALL-001", 1, Certainty.REVIEW_REQUIRED, ParseStatus.PARTIAL, null));
        }
        return new AnalysisResult(List.copyOf(output), ParseStatus.PARTIAL, null);
    }
}
