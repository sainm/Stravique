package dev.stravique.transformation;

import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationAutomation;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemStatus;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationRisk;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebXmlJakartaNamespaceRuleTest {

    private final WebXmlJakartaNamespaceRule rule = new WebXmlJakartaNamespaceRule();

    @Test
    void rewritesTheJavaEeDescriptorToJakartaEe() {
        String source = """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="http://java.sun.com/xml/ns/javaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
                         version="2.5">
                    <listener>
                        <listener-class>com.example.legacy.LegacyStartupListener</listener-class>
                    </listener>
                </web-app>
                """;

        String proposed = rule.propose(source, "web-app/src/main/webapp/WEB-INF/web.xml");

        assertThat(proposed)
                .contains("xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"")
                .contains("https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd")
                .contains("version=\"6.0\"")
                .doesNotContain("java.sun.com/xml/ns/javaee")
                .contains("<listener-class>com.example.legacy.LegacyStartupListener</listener-class>");
        rule.validate(proposed);
    }

    @Test
    void rewritesTheLegacyJ2eeNamespace() {
        String source = """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="http://java.sun.com/xml/ns/j2ee"
                         xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         version="2.4">
                </web-app>
                """;

        String proposed = rule.propose(source, "web.xml");

        assertThat(proposed)
                .contains("https://jakarta.ee/xml/ns/jakartaee")
                .contains("web-app_6_0.xsd")
                .contains("version=\"6.0\"")
                .doesNotContain("java.sun.com/xml/ns/j2ee");
    }

    @Test
    void refusesUnsupportedDescriptors() {
        String jakarta = """
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0"></web-app>
                """;
        String unsupportedVersion = """
                <web-app xmlns="http://java.sun.com/xml/ns/javaee" version="9.0"></web-app>
                """;
        String otherRoot = """
                <struts-config xmlns="http://java.sun.com/xml/ns/javaee" version="2.5"></struts-config>
                """;
        String singleQuoted = """
                <web-app xmlns='http://java.sun.com/xml/ns/javaee' version='2.5'></web-app>
                """;

        assertThatThrownBy(() -> rule.propose(jakarta, "web.xml"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(unsupportedVersion, "web.xml"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(otherRoot, "web.xml"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(singleQuoted, "web.xml"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesMalformedXmlAndInvalidProposals() {
        assertThatThrownBy(() -> rule.propose("<web-app", "web.xml"))
                .isInstanceOf(TransformationInvalidException.class);
        assertThatThrownBy(() -> rule.validate(
                "<web-app xmlns=\"http://java.sun.com/xml/ns/javaee\" version=\"2.5\"/>"))
                .isInstanceOf(TransformationInvalidException.class);
    }

    @Test
    void supportsOnlyWebXmlDescriptors() {
        ProjectFile webXml = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "web.xml", "web.xml", FileType.XML,
                10, null, "hash", false);
        ProjectFile otherXml = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "struts-config.xml", "struts-config.xml", FileType.XML,
                10, null, "hash", false);
        MigrationItem configuration = item(MigrationItemType.CONFIGURATION,
                MigrationTarget.SPRING_BOOT_CONFIG);
        MigrationItem action = item(MigrationItemType.ACTION_CONTROLLER,
                MigrationTarget.SPRING_MVC);

        assertThat(rule.supports(configuration, webXml)).isTrue();
        assertThat(rule.supports(configuration, otherXml)).isFalse();
        assertThat(rule.supports(action, webXml)).isFalse();
        assertThat(rule.fileNameHint()).isEqualTo("web.xml");
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}
