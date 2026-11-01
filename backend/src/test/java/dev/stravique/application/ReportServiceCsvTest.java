package dev.stravique.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceCsvTest {

    @Test
    void prefixesValuesASpreadsheetWouldEvaluate() {
        assertThat(ReportService.csvValue("=cmd|'/C calc'!A0"))
                .isEqualTo("\"'=cmd|'/C calc'!A0\"");
        assertThat(ReportService.csvValue("+1")).isEqualTo("\"'+1\"");
        assertThat(ReportService.csvValue("-1")).isEqualTo("\"'-1\"");
        assertThat(ReportService.csvValue("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(ReportService.csvValue("\tvalue")).isEqualTo("\"'\tvalue\"");
    }

    @Test
    void leavesOrdinaryTextAlone() {
        assertThat(ReportService.csvValue("OrderAction.java"))
                .isEqualTo("\"OrderAction.java\"");
        assertThat(ReportService.csvValue("a = b")).isEqualTo("\"a = b\"");
        assertThat(ReportService.csvValue("")).isEqualTo("\"\"");
    }

    @Test
    void stillDoublesQuotesAndKeepsEverythingInsideOneCell() {
        assertThat(ReportService.csvValue("say \"hi\""))
                .isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportService.csvValue("line1\nline2"))
                .isEqualTo("\"line1\nline2\"");
    }
}
