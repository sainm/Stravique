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

class S2jdbcQuerySpringJdbcRuleTest {

    private final S2jdbcQuerySpringJdbcRule rule = new S2jdbcQuerySpringJdbcRule();

    @Test
    void rewritesAReadOnlySelectQueryToJdbcTemplate() {
        String source = """
                package example;

                import com.example.Order;
                import org.seasar.extension.jdbc.JdbcManager;

                import java.util.List;

                public class OrderQueryDao {
                    public JdbcManager jdbcManager;

                    public List<Order> findByStatus(String status) {
                        return jdbcManager.selectBySql(Order.class,
                                "SELECT * FROM orders WHERE status = ?", status)
                                .getResultList();
                    }
                }
                """;

        String proposed = rule.propose(source, "OrderQueryDao.java");

        assertThat(proposed)
                .contains("import org.springframework.jdbc.core.JdbcTemplate;")
                .contains("import org.springframework.jdbc.core.BeanPropertyRowMapper;")
                .contains("public JdbcTemplate jdbcManager;")
                .contains("jdbcManager.query(\"SELECT * FROM orders WHERE status = ?\", "
                        + "new BeanPropertyRowMapper<>(Order.class), status)")
                .doesNotContain("selectBySql")
                .doesNotContain("getResultList")
                .doesNotContain("java.sql.Types");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
    }

    @Test
    void callsTheRenamedFieldByTheNameItAlreadyHas() {
        String source = """
                package example;

                import com.example.Order;
                import org.seasar.extension.jdbc.JdbcManager;

                import java.util.List;

                public class OrderLookup {
                    public JdbcManager manager;

                    public List<Order> findAll() {
                        return manager.selectBySql(Order.class, "SELECT * FROM orders")
                                .getResultList();
                    }
                }
                """;

        String proposed = rule.propose(source, "OrderLookup.java");

        assertThat(proposed)
                .contains("public JdbcTemplate manager;")
                .contains("manager.query(\"SELECT * FROM orders\", "
                        + "new BeanPropertyRowMapper<>(Order.class))")
                .doesNotContain("jdbcTemplate");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
    }

    @Test
    void rejectsUnsupportedJdbcManagerCalls() {
        String source = """
                package example;

                import org.seasar.extension.jdbc.JdbcManager;

                public class OrderDao {
                    public JdbcManager jdbcManager;

                    public void call() {
                        jdbcManager.callBySql("EXEC refresh_orders");
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source, "OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("requires manual review");
    }

    @Test
    void rewritesAWriteStatementWithItsDeclaredParameterTypes() {
        String source = """
                package example;

                import org.seasar.extension.jdbc.JdbcManager;

                public class OrderDao {
                    public JdbcManager jdbcManager;

                    public void flush() {
                        jdbcManager.updateBySql("UPDATE orders SET status = ? WHERE id = ?",
                                String.class, Long.class).execute("ACTIVE", 1L);
                    }
                }
                """;

        String proposed = rule.propose(source, "OrderDao.java");

        assertThat(proposed)
                .contains("import java.sql.Types;")
                .contains("jdbcManager.update(\"UPDATE orders SET status = ? WHERE id = ?\", "
                        + "new Object[]{\"ACTIVE\", 1L}, new int[]{Types.VARCHAR, Types.BIGINT})")
                .doesNotContain("updateBySql")
                .doesNotContain("execute(");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
    }

    @Test
    void rewritesMixedReadAndWriteChainsInOneFile() {
        String source = """
                package example;

                import com.example.Order;
                import org.seasar.extension.jdbc.JdbcManager;

                import java.util.List;

                public class OrderDao {
                    public JdbcManager jdbcManager;

                    public List<Order> findAll() {
                        return jdbcManager.selectBySql(Order.class, "SELECT * FROM orders")
                                .getResultList();
                    }

                    public void remove(long id) {
                        jdbcManager.deleteBySql("DELETE FROM orders WHERE id = ?", Long.class)
                                .execute(id);
                    }
                }
                """;

        String proposed = rule.propose(source, "OrderDao.java");

        assertThat(proposed)
                .contains("jdbcManager.query(\"SELECT * FROM orders\", "
                        + "new BeanPropertyRowMapper<>(Order.class))")
                .contains("jdbcManager.update(\"DELETE FROM orders WHERE id = ?\", "
                        + "new Object[]{id}, new int[]{Types.BIGINT})")
                .doesNotContain("selectBySql")
                .doesNotContain("deleteBySql");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
    }

    @Test
    void mapsAdditionalVettedParameterTypes() {
        String source = """
                package example;

                import org.seasar.extension.jdbc.JdbcManager;

                public class OrderDao {
                    public JdbcManager jdbcManager;

                    public void archive() {
                        jdbcManager.updateBySql(
                                "UPDATE orders SET created_on = ?, created_at = ?, seq = ?, tiny = ?",
                                LocalDate.class, LocalDateTime.class, BigInteger.class, Byte.class)
                                .execute(date, at, sequence, flag);
                    }
                }
                """;

        String proposed = rule.propose(source, "OrderDao.java");

        assertThat(proposed)
                .contains("new int[]{Types.DATE, Types.TIMESTAMP, Types.NUMERIC, Types.TINYINT}")
                .doesNotContain("updateBySql");
        assertThat(StaticJavaParser.parse(proposed)).isNotNull();
        assertThat(rule.ruleVersion()).isEqualTo("1.3.0");
    }

    @Test
    void rejectsAWriteWhoseBoundParametersDoNotMatchTheDeclaredTypes() {
        String source = """
                package example;

                import org.seasar.extension.jdbc.JdbcManager;

                public class OrderDao {
                    public JdbcManager jdbcManager;

                    public void flush() {
                        jdbcManager.updateBySql("UPDATE orders SET status = ?", String.class)
                                .execute();
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source, "OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("does not match the declared parameter types");
    }

    @Test
    void mapsQualifiedDateTypesAndRejectsTheAmbiguousSimpleName() {
        String source = """
                package example;

                import org.seasar.extension.jdbc.JdbcManager;

                public class OrderDao {
                    public JdbcManager jdbcManager;

                    public void archive(java.util.Date cutoff, java.sql.Date day) {
                        jdbcManager.updateBySql("DELETE FROM orders WHERE created < ? AND day = ?",
                                java.util.Date.class, java.sql.Date.class).execute(cutoff, day);
                    }
                }
                """;

        String proposed = rule.propose(source, "OrderDao.java");

        assertThat(proposed)
                .contains("new int[]{Types.TIMESTAMP, Types.DATE}")
                .doesNotContain("updateBySql");

        String ambiguous = """
                package example;

                import org.seasar.extension.jdbc.JdbcManager;

                public class OrderDao {
                    public JdbcManager jdbcManager;

                    public void archive(java.util.Date cutoff) {
                        jdbcManager.updateBySql("DELETE FROM orders WHERE created < ?",
                                Date.class).execute(cutoff);
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(ambiguous, "OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("Date");
    }

    @Test
    void supportsOnlyDataAccessItemsForSpringJdbc() {
        ProjectFile file = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "OrderQueryDao.java", "OrderQueryDao.java", FileType.JAVA,
                10, null, "hash", false);
        MigrationItem data = item(MigrationItemType.DATA_ACCESS, MigrationTarget.SPRING_JDBC);
        MigrationItem component = item(MigrationItemType.COMPONENT_BEAN, MigrationTarget.SPRING_DI);

        assertThat(rule.supports(data, file)).isTrue();
        assertThat(rule.supports(component, file)).isFalse();
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}