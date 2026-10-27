package dev.stravique.i18n;

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

class MessageBundleTest {

    private static final String[] BUNDLES = {
            "i18n/messages.properties",
            "i18n/messages_zh_CN.properties",
            "i18n/messages_ja_JP.properties"
    };

    @Test
    void everyLocaleExposesTheSameKeys() throws IOException {
        Set<String> base = keys("i18n/messages.properties");
        assertThat(base).isNotEmpty();
        for (String bundle : BUNDLES) {
            assertThat(keys(bundle)).describedAs(bundle).isEqualTo(base);
        }
    }

    @Test
    void noLocalizedValueIsBlank() throws IOException {
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            for (String key : properties.stringPropertyNames()) {
                assertThat(properties.getProperty(key)).describedAs(bundle + " " + key).isNotBlank();
            }
        }
    }

    @Test
    void reportColumnsAreLocalizedPerReportType() throws IOException {
        String[] columns = {
                "id", "path", "type", "moduleId", "sizeBytes", "encoding", "lastModifiedAt",
                "scanStatus", "parseStatus", "parseError", "frameworkApiCount", "generated",
                "changeType", "structureRole", "sha256", "name", "sourceRoots", "testRoots",
                "resourceRoots", "webRoots", "configRoots", "sqlRoots", "moduleDependencies",
                "technologyKey", "category", "status", "apiEvidenceCount",
                "configurationEvidenceCount", "dependencyEvidenceCount", "totalEvidenceCount",
                "ruleIds", "firstEvidencePath", "firstEvidenceLine", "analyzerVersion",
                "analyzedAt", "lineStart", "lineEnd", "analysisKind", "objectKey", "summary",
                "ruleId", "evidence", "certainty", "metadata"
        };
        String[] types = {"FILES", "STRUCTURE", "TECHNOLOGY", "FRAMEWORKS", "CONFIGURATION",
                "PARSE_ERRORS", "REVIEWS"};
        for (String bundle : BUNDLES) {
            Set<String> available = keys(bundle);
            assertThat(available).describedAs(bundle).contains("report.markdownEmpty");
            for (String column : columns) {
                assertThat(available).describedAs(bundle).contains("report.column." + column);
            }
            for (String type : types) {
                assertThat(available).describedAs(bundle).contains("report.title." + type);
            }
        }
    }

    private static Set<String> keys(String resource) throws IOException {
        return new TreeSet<>(load(resource).stringPropertyNames());
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = MessageBundleTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).describedAs(resource).isNotNull();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
