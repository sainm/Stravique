package dev.stravique.inventory;

import dev.stravique.domain.FileType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JavaInventoryAnalyzerTest {

    private final JavaInventoryAnalyzer analyzer = new JavaInventoryAnalyzer(new FindingFactory());

    @Test
    void emitsFrameworkEvidenceWithSourceLinesAndDynamicReviewItems() {
        String source = """
                package example;

                import org.seasar.framework.container.annotation.twig.Component;
                import org.seasar.struts.annotation.Execute;

                @Component
                public class OrderAction {
                    @Execute
                    public String index() throws Exception {
                        Class.forName("example.Extension");
                        return "index.jsp";
                    }
                }
                """;
        TechEvidenceLedger ledger = new TechEvidenceLedger();
        AnalyzerContext context = new AnalyzerContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "src/main/java/example/OrderAction.java", FileType.JAVA, source,
                "test-analyzer", ledger
        );

        AnalysisResult result = analyzer.analyze(context);

        assertThat(result.findings())
                .anyMatch(finding -> finding.getObjectKey().equals("SASTRUTS")
                        && finding.getLineStart() == 4
                        && finding.getEvidence().contains("org.seasar.struts.annotation.Execute"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getObjectKey().equals("DYNAMIC_CLASS_LOADING")
                        && finding.getEvidence().contains("Class.forName"));
        assertThat(ledger.keys()).contains("SASTRUTS", "SEASAR2", "JAVA");
    }

    @Test
    void marksJavaParseFailureWithoutTextFallbackClaims() {
        AnalyzerContext context = new AnalyzerContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Broken.java", FileType.JAVA, "public class Broken {", "test-analyzer",
                new TechEvidenceLedger()
        );

        AnalysisResult result = analyzer.analyze(context);

        assertThat(result.parseStatus().name()).isEqualTo("FAILED");
        assertThat(result.findings()).singleElement()
                .matches(finding -> finding.getCertainty().name().equals("PARSE_FAILED"))
                .matches(finding -> finding.getRuleId().equals("JAVA-PARSE-001"));
    }
}
