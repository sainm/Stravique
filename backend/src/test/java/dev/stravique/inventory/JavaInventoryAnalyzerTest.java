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
    void capturesSaStrutsValidationFormBindingContainerStartupAndTransactionImports() {
        String source = """
                package example;

                import org.seasar.struts.annotation.ActionForm;
                import org.seasar.struts.annotation.Required;
                import org.seasar.framework.container.factory.SingletonS2ContainerFactory;
                import org.seasar.extension.tx.TransactionManager;

                public class EditAction {
                    @ActionForm
                    protected OrderForm orderForm;
                    @Required
                    public String customerName;

                    public void boot() {
                        SingletonS2ContainerFactory.init();
                    }
                }
                """;
        TechEvidenceLedger ledger = new TechEvidenceLedger();
        AnalyzerContext context = new AnalyzerContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "web-app/src/main/java/example/EditAction.java", FileType.JAVA, source,
                "test-analyzer", ledger
        );

        AnalysisResult result = analyzer.analyze(context);

        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JAVA-SAST-FORM-001")
                        && finding.getLineStart() == 9);
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JAVA-SAST-VALIDATION-001")
                        && finding.getLineStart() == 11
                        && finding.getObjectKey().equals("SASTRUTS"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JAVA-SEASAR-CONTAINER-001")
                        && finding.getLineStart() == 15);
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JAVA-S2JDBC-TX-001")
                        && finding.getLineStart() == 6);
        assertThat(ledger.keys()).contains("SASTRUTS", "SEASAR2", "S2JDBC");
    }

    @Test
    void capturesStrutsValidationAndFindForwardCalls() {
        String source = """
                package example;

                import org.apache.struts.action.Action;
                import org.apache.struts.action.ActionMapping;

                public class LegacyAction extends Action {

                    public void validate(ActionMapping mapping) {
                    }

                    public void go(ActionMapping mapping) {
                        mapping.findForward("list");
                    }
                }
                """;
        TechEvidenceLedger ledger = new TechEvidenceLedger();
        AnalyzerContext context = new AnalyzerContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "src/main/java/example/LegacyAction.java", FileType.JAVA, source,
                "test-analyzer", ledger
        );

        AnalysisResult result = analyzer.analyze(context);

        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JAVA-STR1-VALIDATION-001")
                        && finding.getLineStart() == 8);
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JAVA-STR1-FORWARD-001")
                        && finding.getLineStart() == 12
                        && finding.getMetadata().contains("list")
                        && finding.getObjectKey().equals("STRUTS1"));
        assertThat(ledger.keys()).contains("STRUTS1");
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
