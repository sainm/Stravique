package dev.stravique.transformation;

import com.github.javaparser.StaticJavaParser;
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

class SaStrutsValidationSpringRuleTest {

    private final SaStrutsValidationSpringRule rule = new SaStrutsValidationSpringRule();

    @Test
    void rewritesPlainRequiredToBeanValidationNotNull() {
        String source = """
                package example;

                import org.seasar.struts.annotation.Required;

                public class OrderAction {

                    @Required
                    public String customerName;

                    @Required
                    public String status;
                }
                """;

        String proposed = rule.propose(source, "OrderAction.java");

        assertThat(proposed)
                .contains("import jakarta.validation.constraints.NotNull;")
                .doesNotContain("org.seasar.struts.annotation.Required");
        assertThat(proposed.split("@NotNull", -1)).hasSize(3);
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
        assertThat(rule.ruleId()).isEqualTo("SASTRUTS-VALIDATION-BEAN");
    }

    @Test
    void rejectsRequiredWithAttributes() {
        String source = """
                package example;

                import org.seasar.struts.annotation.Required;

                public class OrderAction {
                    @Required(message = "required")
                    public String customerName;
                }
                """;

        assertThatThrownBy(() -> rule.propose(source, "OrderAction.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void supportsOnlyValidationItemsForSpringValidation() {
        ProjectFile file = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "OrderAction.java", "OrderAction.java", FileType.JAVA,
                10, null, "hash", false);
        MigrationItem validation = item(MigrationItemType.VALIDATION,
                MigrationTarget.SPRING_VALIDATION);
        MigrationItem component = item(MigrationItemType.COMPONENT_BEAN,
                MigrationTarget.SPRING_DI);

        assertThat(rule.supports(validation, file)).isTrue();
        assertThat(rule.supports(component, file)).isFalse();
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}
