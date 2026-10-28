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

class SeasarDiSpringRuleTest {

    private final SeasarDiSpringRule rule = new SeasarDiSpringRule();

    @Test
    void replacesSeasarComponentAndBindingWithSpringAnnotations() {
        String source = """
                package example;

                import org.seasar.framework.container.annotation.twig.Component;
                import org.seasar.framework.container.annotation.twig.Binding;

                @Component(instance = "singleton")
                public class OrderService {

                    @Binding
                    public OrderDao orderDao;
                }
                """;

        String proposed = rule.propose(source, "src/OrderService.java");

        assertThat(proposed)
                .contains("import org.springframework.stereotype.Component;")
                .contains("import org.springframework.beans.factory.annotation.Autowired;")
                .contains("@Autowired")
                .doesNotContain("org.seasar.framework")
                .doesNotContain("@Binding");
        assertThat(proposed).contains("@Component");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
        assertThat(rule.ruleId()).isEqualTo("SEASAR-DI-SPRING-DI");
    }

    @Test
    void keepsAComponentNameAndRemovesSingletonInstance() {
        String source = """
                package example;

                import org.seasar.framework.container.annotation.tiger.Component;

                @Component(name = "orderService", instance = "singleton")
                public class OrderService {
                }
                """;

        String proposed = rule.propose(source, "OrderService.java");

        assertThat(proposed).contains("@Component(\"orderService\")");
        assertThat(proposed).doesNotContain("instance");
    }

    @Test
    void rejectsComponentScopesThatSpringCannotMapWithoutReview() {
        String source = """
                package example;

                import org.seasar.framework.container.annotation.tiger.Component;

                @Component(instance = "prototype")
                public class OrderService {
                }
                """;

        assertThatThrownBy(() -> rule.propose(source, "OrderService.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void supportsOnlyComponentItemsForSpringDi() {
        ProjectFile file = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "src/OrderService.java", "OrderService.java", FileType.JAVA,
                10, null, "hash", false);
        MigrationItem supported = item(MigrationItemType.COMPONENT_BEAN, MigrationTarget.SPRING_DI);
        MigrationItem other = item(MigrationItemType.ACTION_CONTROLLER, MigrationTarget.SPRING_MVC);

        assertThat(rule.supports(supported, file)).isTrue();
        assertThat(rule.supports(other, file)).isFalse();
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}
