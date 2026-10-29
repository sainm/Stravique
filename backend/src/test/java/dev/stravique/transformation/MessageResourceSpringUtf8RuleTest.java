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

class MessageResourceSpringUtf8RuleTest {

    private final MessageResourceSpringUtf8Rule rule = new MessageResourceSpringUtf8Rule();

    @Test
    void convertsNonAsciiEscapesAndPreservesTheRestOfTheBundle() {
        String source = "# order labels\n"
                + "order.list.title=\\u6ce8\\u6587\\u4e00\\u89a7\n"
                + "common.search=Search\n"
                + "escaped=\\\\u0041\n"
                + "continued=first \\\n"
                + "second=\\u6ce8\n"
                + "emoji=\\uD83D\\uDE00\n";

        String proposed = rule.propose(source, "web-app/src/main/resources/messages.properties");

        assertThat(proposed)
                .contains("# order labels")
                .contains("order.list.title=注文一覧")
                .contains("common.search=Search")
                .contains("escaped=\\\\u0041")
                .contains("continued=first \\\nsecond=\\u6ce8")
                .contains("emoji=😀");
        assertThat(rule.proposedEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void refusesBundlesWithoutNonAsciiEscapes() {
        String source = "title=Orders\nmarker=\\u0041\n";

        assertThatThrownBy(() -> rule.propose(source, "messages.properties"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesMalformedEscapes() {
        String source = "broken=\\u12\n";

        assertThatThrownBy(() -> rule.propose(source, "messages.properties"))
                .isInstanceOf(TransformationInvalidException.class);
    }

    @Test
    void supportsOnlyMessageResourcesForSpringMessages() {
        ProjectFile properties = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "messages.properties", "messages.properties",
                FileType.PROPERTIES, 10, null, "hash", false);
        ProjectFile java = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Order.java", "Order.java", FileType.JAVA,
                10, null, "hash", false);
        MigrationItem message = item(MigrationItemType.MESSAGE_RESOURCE,
                MigrationTarget.SPRING_MESSAGES);
        MigrationItem validation = item(MigrationItemType.VALIDATION,
                MigrationTarget.SPRING_VALIDATION);

        assertThat(rule.supports(message, properties)).isTrue();
        assertThat(rule.supports(message, java)).isFalse();
        assertThat(rule.supports(validation, properties)).isFalse();
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.LOW, MigrationItemStatus.READY,
                "TEST");
    }
}
