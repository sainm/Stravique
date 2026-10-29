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
import dev.stravique.domain.TransformationArtifactType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrutsControllerSkeletonRuleTest {

    private final StrutsControllerSkeletonRule rule = new StrutsControllerSkeletonRule();

    @Test
    void createsAControllerSkeletonFromExplicitActionMappings() {
        String source = """
                package example;

                import org.seasar.struts.annotation.Execute;

                public class OrderAction {
                    @Execute
                    public String index() {
                        return "list.jsp";
                    }

                    @Execute
                    public String save() {
                        return "edit.jsp";
                    }
                }
                """;

        TransformationDraft draft = rule.propose(request(source, "src/example/OrderAction.java",
                List.of(
                        new ControllerRoute("/orders", "example.OrderAction",
                                "/WEB-INF/jsp/order/list.jsp", "list"),
                        new ControllerRoute("/orders/save", "example.OrderAction",
                                "/WEB-INF/jsp/order/edit.jsp", "edit")
                )));

        assertThat(draft.artifactType()).isEqualTo(TransformationArtifactType.CREATE_FILE);
        assertThat(draft.targetRelativePath()).isEqualTo("src/example/OrderActionController.java");
        assertThat(draft.proposedText())
                .contains("import org.springframework.stereotype.Controller;")
                .contains("import org.springframework.web.bind.annotation.RequestMapping;")
                .contains("@Controller")
                .contains("public class OrderActionController")
                .contains("@RequestMapping(\"/orders\")")
                .contains("public String index()")
                .contains("return \"order/list\";")
                .contains("@RequestMapping(\"/orders/save\")")
                .contains("public String save()")
                .contains("return \"order/edit\";");
        assertThat(StaticJavaParser.parse(draft.proposedText())).isNotNull();
    }

    @Test
    void refusesToInventRoutesWithoutExplicitMappings() {
        String source = """
                package example;

                public class OrderAction {
                    public String index() {
                        return "list.jsp";
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(request(source, "OrderAction.java", List.of())))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void ignoresRoutesWithoutAForwardOrFromAnotherPackage() {
        String source = """
                package example;

                public class OrderAction {
                    @Execute
                    public String index() {
                        return "list.jsp";
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(request(source, "OrderAction.java", List.of(
                new ControllerRoute("/other", "other.OrderAction",
                        "/WEB-INF/jsp/order/list.jsp", "list"),
                new ControllerRoute("/orders", "example.OrderAction", "", "list")
        ))))
                .isInstanceOf(TransformationUnsupportedException.class);

        TransformationDraft draft = rule.propose(request(source, "OrderAction.java", List.of(
                new ControllerRoute("/other", "other.OrderAction",
                        "/WEB-INF/jsp/other/list.jsp", "list"),
                new ControllerRoute("/orders", "example.OrderAction",
                        "/WEB-INF/jsp/order/list.jsp", "list")
        )));

        assertThat(draft.proposedText())
                .contains("@RequestMapping(\"/orders\")")
                .doesNotContain("/other");
    }

    @Test
    void supportsOnlyActionItemsForSpringMvc() {
        ProjectFile file = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "OrderAction.java", "OrderAction.java", FileType.JAVA,
                10, null, "hash", false);
        MigrationItem action = item(MigrationItemType.ACTION_CONTROLLER, MigrationTarget.SPRING_MVC);
        MigrationItem data = item(MigrationItemType.DATA_ACCESS, MigrationTarget.SPRING_JDBC);

        assertThat(rule.supports(action, file)).isTrue();
        assertThat(rule.supports(data, file)).isFalse();
    }

    private TransformationRequest request(String source, String relativePath,
                                          List<ControllerRoute> routes) {
        return new TransformationRequest(item(MigrationItemType.ACTION_CONTROLLER,
                MigrationTarget.SPRING_MVC), ProjectFile.discovered(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), relativePath, "OrderAction.java",
                FileType.JAVA, source.length(), null, "hash", false), source, relativePath, routes);
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}