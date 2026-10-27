package dev.stravique.inventory;

import dev.stravique.domain.FileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileClassifierTest {

    private final FileClassifier classifier = new FileClassifier();

    @Test
    void classifiesLegacyProjectFiles() {
        assertThat(classifier.classify("pom.xml")).contains(FileType.MAVEN_POM);
        assertThat(classifier.classify("settings.gradle")).contains(FileType.GRADLE);
        assertThat(classifier.classify("web/WEB-INF/web.xml")).contains(FileType.XML);
        assertThat(classifier.classify("WEB-INF/dicon/app.dicon")).contains(FileType.DICON);
        assertThat(classifier.classify("WEB-INF/tld/custom.tld")).contains(FileType.TLD);
        assertThat(classifier.classify("src/main/webapp/order.jsp")).contains(FileType.JSP);
        assertThat(classifier.classify("src/test/java/example/OrderTest.java")).contains(FileType.TEST);
        assertThat(classifier.classify("src/main/resources/messages.properties"))
                .contains(FileType.PROPERTIES);
        assertThat(classifier.classify("README.md")).isEmpty();
    }
}
