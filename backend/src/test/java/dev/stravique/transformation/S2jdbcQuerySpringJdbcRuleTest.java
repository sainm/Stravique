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
                .doesNotContain("getResultList");
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

                    public void save() {
                        jdbcManager.updateBySql("UPDATE orders SET status = ?").execute();
                    }
                }
                """;

        assertThatThrownBy(() -> rule.propose(source, "OrderDao.java"))
                .isInstanceOf(TransformationUnsupportedException.class);
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