package dev.stravique.transformation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasarTransactionSpringRuleTest {

    private final SeasarTransactionSpringRule rule = new SeasarTransactionSpringRule();

    @Test
    void rewritesTheBareAnnotationAndReplacesTheImport() {
        String source = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.annotation.TransactionAttribute;

                public class OrderDao {

                    @TransactionAttribute
                    public void flush() {
                        repository.update();
                    }
                }
                """;

        String proposed = rule.propose(source, "domain/src/main/java/com/example/legacy/dao/OrderDao.java");

        assertThat(proposed)
                .contains("import org.springframework.transaction.annotation.Transactional;")
                .doesNotContain("org.seasar.extension.tx")
                .contains("    @Transactional\n    public void flush()")
                .contains("repository.update();");
        rule.validate(proposed);
    }

    @Test
    void dropsTheSeasarImportWhenSpringIsAlreadyImported() {
        String source = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.annotation.TransactionAttribute;
                import org.springframework.transaction.annotation.Transactional;

                public class OrderDao {

                    @TransactionAttribute
                    public void flush() {
                    }

                    @Transactional
                    public void reload() {
                    }
                }
                """;

        String proposed = rule.propose(source, "domain/src/main/java/com/example/legacy/dao/OrderDao.java");

        assertThat(proposed)
                .doesNotContain("org.seasar.extension.tx")
                .contains("    @Transactional\n    public void flush()")
                .contains("    @Transactional\n    public void reload()");
        assertThat(proposed).isEqualTo(source
                .replace("import org.seasar.extension.tx.annotation.TransactionAttribute;\n", "")
                .replace("@TransactionAttribute", "@Transactional"));
    }

    @Test
    void refusesAParameterizedAnnotation() {
        String source = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.TransactionAttributeType;
                import org.seasar.extension.tx.annotation.TransactionAttribute;

                public class OrderDao {

                    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
                    public void flush() {
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("attributes require manual review");
    }

    @Test
    void refusesASourceWithoutTheSeasarImport() {
        String source = """
                package com.example.legacy.dao;

                public class OrderDao {

                    @TransactionAttribute
                    public void flush() {
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("does not import the Seasar2 transaction annotation");
    }

    @Test
    void refusesAnImportWithNoAnnotation() {
        String source = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.annotation.TransactionAttribute;

                public class OrderDao {

                    public void flush() {
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("does not annotate a type or method");
    }
}
