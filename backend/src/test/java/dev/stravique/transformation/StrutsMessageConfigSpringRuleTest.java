package dev.stravique.transformation;

import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationAutomation;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemStatus;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationRisk;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.TransformationArtifactType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrutsMessageConfigSpringRuleTest {

    private final StrutsMessageConfigSpringRule rule = new StrutsMessageConfigSpringRule();

    @Test
    void addsTheSpringMessageBasenameToYaml() {
        String source = """
                <struts-config>
                    <message-resources parameter="messages"/>
                </struts-config>
                """;
        TransformationCompanion target = new TransformationCompanion(
                "web-app/src/main/resources/application.yml",
                "legacy:\n  enabled: true\n", "target-hash", "UTF-8");

        TransformationDraft draft = rule.propose(request(source, target));

        assertThat(draft.artifactType()).isEqualTo(TransformationArtifactType.EDIT_FILE);
        assertThat(draft.targetRelativePath())
                .isEqualTo("web-app/src/main/resources/application.yml");
        assertThat(draft.originalText()).isEqualTo(target.source());
        assertThat(draft.proposedText())
                .contains("legacy:\n  enabled: true\n")
                .contains("spring.messages.basename: \"messages\"\n");
        rule.validate(draft.proposedText(), draft.targetRelativePath());
    }

    @Test
    void addsTheSpringMessageBasenameToProperties() {
        String source = """
                <struts-config>
                    <message-resources parameter="resources.messages"/>
                </struts-config>
                """;
        TransformationCompanion target = new TransformationCompanion(
                "web-app/src/main/resources/application.properties",
                "legacy.enabled=true\n", "target-hash", "UTF-8");

        TransformationDraft draft = rule.propose(request(source, target));

        assertThat(draft.proposedText())
                .contains("legacy.enabled=true\n")
                .contains("spring.messages.basename=resources.messages\n");
        rule.validate(draft.proposedText(), draft.targetRelativePath());
    }

    @Test
    void refusesMissingTargetsAndExistingConfiguration() {
        String source = """
                <struts-config>
                    <message-resources parameter="messages"/>
                </struts-config>
                """;
        TransformationCompanion existing = new TransformationCompanion(
                "web-app/src/main/resources/application.yml",
                "spring.messages.basename: messages\n", "target-hash", "UTF-8");
        TransformationCompanion other = new TransformationCompanion(
                "web-app/src/main/resources/application.properties",
                "legacy.enabled=true\n", "other-hash", "UTF-8");

        assertThatThrownBy(() -> rule.propose(request(source)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(source, existing)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(source, existing, other)))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesSourcesWithoutAMessageResourcesDeclaration() {
        String source = "<struts-config><action-mappings/></struts-config>";
        TransformationCompanion target = new TransformationCompanion(
                "web-app/src/main/resources/application.yml", "legacy:\n", "target-hash", "UTF-8");

        assertThatThrownBy(() -> rule.propose(request(source, target)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request("<struts-config>", target)))
                .isInstanceOf(TransformationInvalidException.class);
    }

    @Test
    void supportsOnlyStrutsConfigItems() {
        ProjectFile file = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "struts-config.xml", "struts-config.xml", FileType.XML,
                10, null, "hash", false);
        MigrationItem configuration = item(MigrationItemType.CONFIGURATION,
                MigrationTarget.SPRING_BOOT_CONFIG);
        MigrationItem other = item(MigrationItemType.MESSAGE_RESOURCE,
                MigrationTarget.SPRING_MESSAGES);

        assertThat(rule.supports(configuration, file)).isTrue();
        assertThat(rule.supports(other, file)).isFalse();
        assertThat(rule.fileNameHint()).isEqualTo("struts-config.xml");
        assertThat(rule.language()).isEqualTo("YAML");
    }

    private TransformationRequest request(String source, TransformationCompanion... companions) {
        return new TransformationRequest(item(MigrationItemType.CONFIGURATION,
                MigrationTarget.SPRING_BOOT_CONFIG), file(), source, "struts-config.xml",
                List.of(), List.of(companions));
    }

    private ProjectFile file() {
        return ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "web-app/src/main/webapp/WEB-INF/struts-config.xml", "struts-config.xml",
                FileType.XML, 10, null, "source-hash", false);
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}
