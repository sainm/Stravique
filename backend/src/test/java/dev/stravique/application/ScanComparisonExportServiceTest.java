package dev.stravique.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScanComparisonExportServiceTest {

    @Test
    void guardsCsvCellsThatASpreadsheetWouldEvaluate() {
        Map<String, String> row = row();
        row.put("status", "ADDED");
        row.put("relativePath", "=cmd|'/C calc'!A0");

        String csv = ScanComparisonExportService.csv(List.of(row));

        assertThat(csv).contains("\"'=cmd|'/C calc'!A0\"");
        assertThat(csv).doesNotContain("\"=cmd");
    }

    @Test
    void guardsEveryTriggerCharacterTheReportExporterUnderstands() {
        for (String value : List.of("+1", "-1", "@SUM(A1)", "\tvalue")) {
            Map<String, String> row = row();
            row.put("relativePath", value);
            assertThat(ScanComparisonExportService.csv(List.of(row)))
                    .as("CSV cell for %s", value)
                    .contains("\"'" + value + "\"");
        }
    }

    @Test
    void doublesQuotesAndKeepsNewlinesInsideOneCell() {
        Map<String, String> row = row();
        row.put("relativePath", "say \"hi\"\nnext");

        assertThat(ScanComparisonExportService.csv(List.of(row)))
                .contains("\"say \"\"hi\"\"\nnext\"");
    }

    @Test
    void leavesOrdinaryPathsAlone() {
        Map<String, String> row = row();
        row.put("relativePath", "web-app/src/OrderAction.java");

        assertThat(ScanComparisonExportService.csv(List.of(row)))
                .contains("\"web-app/src/OrderAction.java\"");
    }

    private Map<String, String> row() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : ScanComparisonExportService.COLUMNS) {
            values.put(column, "");
        }
        return values;
    }
}
