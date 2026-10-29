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

class JavaxToJakartaRuleTest {

    private final JavaxToJakartaRule rule = new JavaxToJakartaRule();

    @Test
    void rewritesSupportedJavaxImportsAndPreservesWildcards() {
        String source = """
                package example;

                import javax.persistence.Entity;
                import javax.servlet.*;
                import javax.validation.constraints.NotNull;

                @Entity
                public class Order {
                    @NotNull
                    public String customerName;
                }
                """;

        String proposed = rule.propose(source, "Order.java");

        assertThat(proposed)
                .contains("import jakarta.persistence.Entity;")
                .contains("import jakarta.servlet.*;")
                .contains("import jakarta.validation.constraints.NotNull;")
                .doesNotContain("import javax.persistence")
                .doesNotContain("import javax.servlet")
                .doesNotContain("import javax.validation");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
    }

    @Test
    void rejectsFilesWithoutSupportedJavaxImports() {
        String source = """
                package example;

                import javax.crypto.Cipher;

                public class Crypto {
                }
                """;

        assertThatThrownBy(() -> rule.propose(source, "Crypto.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void supportsOnlyJakartaNamespaceItems() {
        ProjectFile file = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Order.java", "Order.java", FileType.JAVA,
                10, null, "hash", false);
        MigrationItem jakarta = item(MigrationItemType.JAKARTA_NAMESPACE,
                MigrationTarget.JAKARTA_EE);
        MigrationItem data = item(MigrationItemType.DATA_ACCESS, MigrationTarget.SPRING_JDBC);

        assertThat(rule.supports(jakarta, file)).isTrue();
        assertThat(rule.supports(data, file)).isFalse();
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}
