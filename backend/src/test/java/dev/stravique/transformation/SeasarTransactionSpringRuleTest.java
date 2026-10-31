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
        assertThat(rule.ruleVersion()).isEqualTo("1.1.0");
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
    void rewritesRequiresNewWithPropagation() {
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

        String proposed = rule.propose(source,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java");

        assertThat(proposed)
                .contains("import org.springframework.transaction.annotation.Transactional;")
                .contains("import org.springframework.transaction.annotation.Propagation;")
                .contains("@Transactional(propagation = Propagation.REQUIRES_NEW)")
                .doesNotContain("org.seasar.extension.tx");
        rule.validate(proposed);
    }

    @Test
    void mapsExplicitRequiredToTheDefaultTransactional() {
        String source = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.TransactionAttributeType;
                import org.seasar.extension.tx.annotation.TransactionAttribute;

                public class OrderDao {

                    @TransactionAttribute(TransactionAttributeType.REQUIRED)
                    public void flush() {
                    }
                }
                """;

        String proposed = rule.propose(source,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java");

        assertThat(proposed)
                .contains("@Transactional\n")
                .doesNotContain("Propagation")
                .doesNotContain("org.seasar.extension.tx");
    }

    @Test
    void reusesAnExistingPropagationImport() {
        String source = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.TransactionAttributeType;
                import org.seasar.extension.tx.annotation.TransactionAttribute;
                import org.springframework.transaction.annotation.Propagation;
                import org.springframework.transaction.annotation.Transactional;

                public class OrderDao {

                    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
                    public void flush() {
                    }
                }
                """;

        String proposed = rule.propose(source,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java");

        assertThat(proposed)
                .contains("@Transactional(propagation = Propagation.REQUIRES_NEW)")
                .doesNotContain("org.seasar.extension.tx");
        assertThat(proposed.split("import org.springframework.transaction.annotation.Propagation;",
                -1)).hasSize(2);
    }

    @Test
    void refusesUnsupportedPropagationValuesAndNonEnumValues() {
        String header = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.TransactionAttributeType;
                import org.seasar.extension.tx.annotation.TransactionAttribute;

                public class OrderDao {
                """;

        assertThatThrownBy(() -> rule.propose(header
                + "    @TransactionAttribute(TransactionAttributeType.MANDATORY)\n"
                + "    public void flush() {}\n}\n",
                "OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("requires manual review");
        assertThatThrownBy(() -> rule.propose(header
                + "    @TransactionAttribute(\"REQUIRES_NEW\")\n"
                + "    public void flush() {}\n}\n",
                "OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesADuplicateTransactionalAnnotation() {
        String source = """
                package com.example.legacy.dao;

                import org.seasar.extension.tx.annotation.TransactionAttribute;
                import org.springframework.transaction.annotation.Transactional;

                public class OrderDao {

                    @Transactional
                    @TransactionAttribute
                    public void flush() {
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("existing @Transactional");
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
