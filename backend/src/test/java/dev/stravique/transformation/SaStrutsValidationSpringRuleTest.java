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
        assertThat(rule.ruleVersion()).isEqualTo("1.1.0");
    }

    @Test
    void rewritesLengthAnnotationsToSize() {
        String source = """
                package example;

                import org.seasar.struts.annotation.Length;
                import org.seasar.struts.annotation.MaxLength;
                import org.seasar.struts.annotation.Required;

                public class OrderAction {

                    @Required
                    @MaxLength(120)
                    public String customerName;

                    @Length(min = 2, max = 20)
                    public String status;
                }
                """;

        String proposed = rule.propose(source, "OrderAction.java");

        assertThat(proposed)
                .contains("import jakarta.validation.constraints.NotNull;")
                .contains("import jakarta.validation.constraints.Size;")
                .contains("@Size(max = 120)")
                .contains("@Size(min = 2, max = 20)")
                .doesNotContain("org.seasar.struts.annotation");
        assertThat(proposed.split("@Size", -1)).hasSize(3);
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
    }

    @Test
    void rewritesSizeAnnotationsWithoutARequiredImport() {
        String source = """
                package example;

                import org.seasar.struts.annotation.MinLength;

                public class OrderForm {
                    @MinLength(3)
                    public String name;
                }
                """;

        String proposed = rule.propose(source, "OrderForm.java");

        assertThat(proposed)
                .contains("import jakarta.validation.constraints.Size;")
                .contains("@Size(min = 3)")
                .doesNotContain("jakarta.validation.constraints.NotNull")
                .doesNotContain("org.seasar.struts.annotation");
    }

    @Test
    void reusesAnExistingSizeImportAndRemovesTheSourceImport() {
        String source = """
                package example;

                import jakarta.validation.constraints.Size;
                import org.seasar.struts.annotation.MaxLength;

                public class OrderForm {
                    @MaxLength(5)
                    public String name;
                }
                """;

        String proposed = rule.propose(source, "OrderForm.java");

        assertThat(proposed)
                .contains("import jakarta.validation.constraints.Size;")
                .contains("@Size(max = 5)")
                .doesNotContain("org.seasar.struts.annotation");
        assertThat(proposed.split("import jakarta.validation.constraints.Size;", -1))
                .hasSize(2);
    }

    @Test
    void rejectsLengthAnnotationsThatNeedManualReview() {
        String header = """
                package example;

                import org.seasar.struts.annotation.Length;
                import org.seasar.struts.annotation.MaxLength;
                import org.seasar.struts.annotation.MinLength;

                public class OrderForm {
                """;

        assertThatThrownBy(() -> rule.propose(header
                + "    @Length(min = 1, max = 10, message = \"x\")\n"
                + "    public String name;\n}\n", "OrderForm.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(header
                + "    @MaxLength(value = 5)\n"
                + "    public String name;\n}\n", "OrderForm.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(header
                + "    @MaxLength(5)\n    @MinLength(1)\n"
                + "    public String name;\n}\n", "OrderForm.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(header
                + "    @MaxLength(-1)\n"
                + "    public String name;\n}\n", "OrderForm.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(header
                + "    @Length(min = 9, max = 2)\n"
                + "    public String name;\n}\n", "OrderForm.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void rejectsDuplicateOrConflictingTargets() {
        assertThatThrownBy(() -> rule.propose("""
                package example;

                import jakarta.validation.constraints.Size;
                import org.seasar.struts.annotation.MaxLength;

                public class OrderForm {
                    @Size(max = 5)
                    @MaxLength(5)
                    public String name;
                }
                """, "OrderForm.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose("""
                package example;

                import jakarta.validation.constraints.NotNull;
                import org.seasar.struts.annotation.Required;

                public class OrderAction {
                    @NotNull
                    @Required
                    public String name;
                }
                """, "OrderAction.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose("""
                package example;

                import javax.validation.constraints.NotNull;
                import org.seasar.struts.annotation.Required;

                public class OrderAction {
                    @Required
                    public String name;
                }
                """, "OrderAction.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
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
