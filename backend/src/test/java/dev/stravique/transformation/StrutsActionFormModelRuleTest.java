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

class StrutsActionFormModelRuleTest {

    private final StrutsActionFormModelRule rule = new StrutsActionFormModelRule();

    @Test
    void removesActionFormInheritanceFromAPlainModel() {
        String source = """
                package example;

                import org.apache.struts.action.ActionForm;

                public class OrderForm extends ActionForm {
                    public String customerName;
                }
                """;

        String proposed = rule.propose(source, "OrderForm.java");

        assertThat(proposed).doesNotContain("ActionForm");
        assertThat(proposed).contains("public class OrderForm");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
        assertThat(rule.ruleId()).isEqualTo("STRUTS-ACTIONFORM-MODEL");
    }

    @Test
    void rejectsActionFormLifecycleMethods() {
        String source = """
                package example;

                import org.apache.struts.action.ActionForm;
                import org.apache.struts.action.ActionMapping;

                public class OrderForm extends ActionForm {
                    public void reset(ActionMapping mapping, javax.servlet.http.HttpServletRequest request) {
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source, "OrderForm.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void supportsOnlyFormModelItemsForSpringMvc() {
        ProjectFile file = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "OrderForm.java", "OrderForm.java", FileType.JAVA,
                10, null, "hash", false);
        MigrationItem form = item(MigrationItemType.FORM_MODEL, MigrationTarget.SPRING_MVC);
        MigrationItem action = item(MigrationItemType.ACTION_CONTROLLER, MigrationTarget.SPRING_MVC);

        assertThat(rule.supports(form, file)).isTrue();
        assertThat(rule.supports(action, file)).isFalse();
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}
