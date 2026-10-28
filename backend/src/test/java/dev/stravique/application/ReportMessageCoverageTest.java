package dev.stravique.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A report type or column without a message key silently degrades to its raw identifier in
 * exported Markdown. This test derives the required keys from the declarations themselves so a
 * new report type or column cannot ship without its labels.
 */
class ReportMessageCoverageTest {

    private static final String[] BUNDLES = {
            "i18n/messages.properties",
            "i18n/messages_zh_CN.properties",
            "i18n/messages_ja_JP.properties"
    };

    @Test
    void everyReportTypeDeclaresItsTitleKey() throws IOException {
        for (String bundle : BUNDLES) {
            Set<String> available = keys(bundle);
            for (ReportService.ReportType type : ReportService.ReportType.values()) {
                assertThat(available).describedAs(bundle).contains("report.title." + type.name());
            }
            assertThat(available).describedAs(bundle).contains("report.markdownEmpty");
        }
    }

    @Test
    void everyDeclaredColumnDeclaresItsLabelKey() throws IOException {
        for (String bundle : BUNDLES) {
            Set<String> available = keys(bundle);
            for (ReportService.ReportType type : ReportService.ReportType.values()) {
                for (String column : ReportService.columns(type)) {
                    assertThat(available)
                            .describedAs(bundle + " column " + column + " of " + type)
                            .contains("report.column." + column);
                }
            }
        }
    }

    @Test
    void columnLabelsContainNoOrphans() throws IOException {
        Set<String> declared = new TreeSet<>();
        for (ReportService.ReportType type : ReportService.ReportType.values()) {
            declared.addAll(ReportService.columns(type));
        }
        Set<String> expected = new TreeSet<>();
        declared.forEach(column -> expected.add("report.column." + column));

        for (String bundle : BUNDLES) {
            Set<String> actual = new TreeSet<>();
            for (String key : keys(bundle)) {
                if (key.startsWith("report.column.")) {
                    actual.add(key);
                }
            }
            assertThat(actual).describedAs(bundle).isEqualTo(expected);
        }
    }

    private static Set<String> keys(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream =
                     ReportMessageCoverageTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).describedAs(resource).isNotNull();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return new TreeSet<>(properties.stringPropertyNames());
    }
}
