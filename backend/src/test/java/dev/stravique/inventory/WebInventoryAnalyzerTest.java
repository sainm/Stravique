package dev.stravique.inventory;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ParseStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebInventoryAnalyzerTest {

    private final WebInventoryAnalyzer analyzer = new WebInventoryAnalyzer(new FindingFactory());

    @Test
    void detectsStrutsTagFormsFieldsMessagesAndIgnoresCommentedMarkup() {
        String jsp = """
                <%@ page contentType="text/html; charset=UTF-8" %>
                <%@ taglib prefix="fmt"
                    uri="http://java.sun.com/jsp/jstl/fmt" %>
                <%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
                <%@ taglib prefix="ui" uri="http://example.com/tags/legacy-ui" %>
                <%-- <form action="/ignored"> --%>
                <html:form action="/orders/save">
                    <html:text property="customerName"/>
                    <input name="plainField">
                    <fmt:message key="order.list.title"/>
                    <html:errors/>
                    <ui:emptyState message="orders.empty"/>
                    <script src="/assets/order.js"></script>
                </html:form>
                """;
        TechEvidenceLedger ledger = new TechEvidenceLedger();

        AnalysisResult result = analyzer.analyzeJsp(context(jsp, ledger));

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARTIAL);
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-STRUTS-TAGLIB-001")
                        && finding.getLineStart() == 4);
        assertThat(result.findings())
                .noneMatch(finding -> finding.getRuleId().equals("JSP-FORM-ACTION-001")
                        && finding.getEvidence().contains("/ignored"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-FORM-ACTION-001")
                        && finding.getLineStart() == 7
                        && finding.getObjectKey().equals("FORM_ACTION")
                        && finding.getCertainty() == Certainty.REVIEW_REQUIRED
                        && finding.getMetadata().contains("/orders/save")
                        && finding.getMetadata().contains("html:form"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-FORM-FIELD-001")
                        && finding.getLineStart() == 8
                        && finding.getMetadata().contains("customerName")
                        && finding.getMetadata().contains("html:text"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-FORM-FIELD-001")
                        && finding.getLineStart() == 9
                        && finding.getMetadata().contains("plainField"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-MESSAGE-DISPLAY-001")
                        && finding.getLineStart() == 10
                        && finding.getMetadata().contains("order.list.title"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-STRUTS-MESSAGE-001")
                        && finding.getLineStart() == 11);
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-CUSTOM-TAG-USE-001")
                        && finding.getLineStart() == 12
                        && finding.getMetadata().contains("ui:emptyState"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-STATIC-REF-001")
                        && finding.getLineStart() == 13
                        && finding.getMetadata().contains("/assets/order.js"));
        assertThat(ledger.keys()).contains("JSP", "JSTL", "STRUTS1", "CUSTOM_TAGS");
    }

    @Test
    void keepsPlainHtmlFormsAndFieldsWhileMarkingRoutingAsUnresolved() {
        String jsp = """
                <form action="/orders" method="post">
                    <input name="customerName">
                    <select name="status"><option value="ACTIVE">Active</option></select>
                </form>
                """;
        TechEvidenceLedger ledger = new TechEvidenceLedger();

        AnalysisResult result = analyzer.analyzeJsp(context(jsp, ledger));

        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("JSP-FORM-ACTION-001")
                        && finding.getLineStart() == 1
                        && finding.getCertainty() == Certainty.REVIEW_REQUIRED);
        assertThat(result.findings().stream()
                .filter(finding -> finding.getRuleId().equals("JSP-FORM-FIELD-001")))
                .hasSize(2);
        assertThat(ledger.keys()).contains("JSP");
    }

    private AnalyzerContext context(String text, TechEvidenceLedger ledger) {
        return new AnalyzerContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "web-app/src/main/webapp/WEB-INF/jsp/order/edit.jsp", FileType.JSP, text,
                "test-analyzer", ledger
        );
    }
}
