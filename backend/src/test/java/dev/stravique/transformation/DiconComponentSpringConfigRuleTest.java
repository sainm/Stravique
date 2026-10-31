package dev.stravique.transformation;

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

class DiconComponentSpringConfigRuleTest {

    private final DiconComponentSpringConfigRule rule = new DiconComponentSpringConfigRule();

    @Test
    void generatesAConfigurationForExplicitSingletonComponents() {
        String dicon = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                    <component name="orderQueryDao"
                               class="com.example.legacy.dao.OrderQueryDao"/>
                </components>
                """;
        String service = """
                package com.example.legacy.service;
                public class OrderService {
                }
                """;
        String dao = """
                package com.example.legacy.dao;
                public class OrderQueryDao {
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        service),
                companion("web-app/src/main/java/com/example/legacy/dao/OrderQueryDao.java", dao))));

        assertThat(draft.artifactType()).isEqualTo(TransformationArtifactType.CREATE_FILE);
        assertThat(draft.targetRelativePath()).isEqualTo(
                "web-app/src/main/java/com/example/legacy/SimpleDiconConfiguration.java");
        assertThat(draft.proposedText())
                .contains("package com.example.legacy;")
                .contains("import org.springframework.context.annotation.Configuration;")
                .contains("@Configuration")
                .contains("public class SimpleDiconConfiguration")
                .contains("public com.example.legacy.service.OrderService orderService()")
                .contains("return new com.example.legacy.service.OrderService();")
                .contains("public com.example.legacy.dao.OrderQueryDao orderQueryDao()")
                .contains("return new com.example.legacy.dao.OrderQueryDao();");
        rule.validate(draft.proposedText(), draft.targetRelativePath());
        assertThat(rule.ruleVersion()).isEqualTo("1.6.0");
        assertThat(rule.fileNameHint()).isEqualTo(".dicon");
    }

    @Test
    void generatesConstructorArgumentsAndScopes() {
        String dicon = """
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction"
                               instance="prototype">
                        <arg>orderService</arg>
                    </component>
                </components>
                """;
        String action = """
                package com.example.legacy.action;
                import com.example.legacy.service.OrderService;
                public class OrderAction {
                    public OrderAction(OrderService orderService) {
                    }
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/action/OrderAction.java",
                        action))));

        assertThat(draft.proposedText())
                .contains("import org.springframework.context.annotation.Scope;")
                .contains("@Scope(\"prototype\")")
                .contains("@Qualifier(\"orderService\")"
                        + " com.example.legacy.service.OrderService orderService")
                .contains("return new com.example.legacy.action.OrderAction(orderService);");
        rule.validate(draft.proposedText(), draft.targetRelativePath());
    }

    @Test
    void generatesTypedLiteralPropertiesAndArguments() {
        String propertyDicon = """
                <components>
                    <component name="settings"
                               class="com.example.legacy.service.SettingsService">
                        <property name="name">"demo"</property>
                        <property name="enabled">true</property>
                        <property name="count">3</property>
                        <property name="total">10L</property>
                        <property name="ratio">1.5</property>
                    </component>
                </components>
                """;
        String settings = """
                package com.example.legacy.service;
                public class SettingsService {
                    public String name;
                    public boolean enabled;
                    public int count;
                    public long total;
                    public double ratio;
                }
                """;
        TransformationDraft propertyDraft = rule.propose(request(propertyDicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/SettingsService.java",
                        settings))));
        assertThat(propertyDraft.proposedText())
                .contains("bean.name = \"demo\";")
                .contains("bean.enabled = true;")
                .contains("bean.count = 3;")
                .contains("bean.total = 10L;")
                .contains("bean.ratio = 1.5;")
                .doesNotContain("@Qualifier");

        String argumentDicon = """
                <components>
                    <component name="label"
                               class="com.example.legacy.service.LabelService">
                        <arg>"demo"</arg>
                        <arg>7</arg>
                    </component>
                </components>
                """;
        String label = """
                package com.example.legacy.service;
                public class LabelService {
                    public LabelService(String name, int count) {
                    }
                }
                """;
        TransformationDraft argumentDraft = rule.propose(request(argumentDicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/LabelService.java",
                        label))));
        assertThat(argumentDraft.proposedText())
                .contains("return new com.example.legacy.service.LabelService(\"demo\", 7);")
                .doesNotContain("@Qualifier");
        rule.validate(argumentDraft.proposedText(), argumentDraft.targetRelativePath());
    }

    @Test
    void refusesMismatchedOrEscapedLiterals() {
        String header = """
                <components>
                    <component name="settings"
                               class="com.example.legacy.service.SettingsService">
                """;
        String footer = """
                    </component>
                </components>
                """;
        String settings = """
                package com.example.legacy.service;
                public class SettingsService {
                    public String name;
                    public int count;
                    public long total;
                    public double ratio;
                }
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/java/com/example/legacy/service/SettingsService.java",
                        settings));

        assertThatThrownBy(() -> rule.propose(request(header
                + "<property name=\"name\">3</property>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<property name=\"count\">\"3\"</property>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<property name=\"count\">1.5</property>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<property name=\"total\">99999999999999999999</property>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<property name=\"name\">\"a\\\\b\"</property>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void combinesIncludedDiconsIntoOneConfiguration() {
        String leaf = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """;
        String base = """
                <components>
                    <include path="leaf.dicon"/>
                    <component name="orderQueryDao"
                               class="com.example.legacy.dao.OrderQueryDao"/>
                </components>
                """;
        String root = """
                <components>
                    <include path="base.dicon"/>
                    <include path="leaf.dicon"/>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction"/>
                </components>
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/webapp/WEB-INF/dicon/leaf.dicon", leaf),
                companion("web-app/src/main/webapp/WEB-INF/dicon/base.dicon", base),
                companion("web-app/src/main/webapp/WEB-INF/dicon/root.dicon", root),
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        "package com.example.legacy.service; public class OrderService {}"),
                companion("web-app/src/main/java/com/example/legacy/dao/OrderQueryDao.java",
                        "package com.example.legacy.dao; public class OrderQueryDao {}"),
                companion("web-app/src/main/java/com/example/legacy/action/OrderAction.java",
                        "package com.example.legacy.action; public class OrderAction {}"));

        TransformationDraft draft = rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/root.dicon", root, companions));

        assertThat(draft.targetRelativePath()).isEqualTo(
                "web-app/src/main/java/com/example/legacy/RootDiconConfiguration.java");
        String text = draft.proposedText();
        assertThat(text)
                .contains("public com.example.legacy.service.OrderService orderService()")
                .contains("public com.example.legacy.dao.OrderQueryDao orderQueryDao()")
                .contains("public com.example.legacy.action.OrderAction orderAction()");
        assertThat(text.split("orderService\\(\\)", -1).length - 1).isEqualTo(1);
        assertThat(text.indexOf("orderService()")).isLessThan(text.indexOf("orderQueryDao()"));
        assertThat(text.indexOf("orderQueryDao()")).isLessThan(text.indexOf("orderAction()"));
        rule.validate(text, draft.targetRelativePath());
        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/base.dicon", base, companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("included by another");
        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/leaf.dicon", leaf, companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("included by another");
    }

    @Test
    void refusesCyclesMissingCrossModuleAndInvalidIncludes() {
        String header = """
                <components>
                    <include path="%s"/>
                </components>
                """;
        String base = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """;
        String selfCycle = header.formatted("self.dicon");
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/webapp/WEB-INF/dicon/base.dicon", base),
                companion("web-app/src/main/webapp/WEB-INF/dicon/self.dicon", selfCycle),
                companion("domain/src/main/webapp/WEB-INF/dicon/other.dicon", base),
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        "package com.example.legacy.service; public class OrderService {}"));

        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/self.dicon", selfCycle, companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("cycle");
        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/missing.dicon",
                header.formatted("absent.dicon"), companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("not a project dicon");
        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/cross.dicon",
                header.formatted("../../../../../../domain/src/main/webapp/"
                        + "WEB-INF/dicon/other.dicon"),
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("Cross-module");
        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/invalid.dicon",
                "<components><include path=\"base.dicon\" extra=\"x\"/></components>",
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/invalid.dicon",
                "<components><include path=\"base.dicon\"><x/></include></components>",
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesDuplicateBeansAcrossIncludes() {
        String base = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """;
        String root = """
                <components>
                    <include path="base.dicon"/>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/webapp/WEB-INF/dicon/base.dicon", base),
                companion("web-app/src/main/webapp/WEB-INF/dicon/root.dicon", root),
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        "package com.example.legacy.service; public class OrderService {}"));

        assertThatThrownBy(() -> rule.propose(includeRequest(
                "web-app/src/main/webapp/WEB-INF/dicon/root.dicon", root, companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("Duplicate component");
    }

    @Test
    void refusesLiteralArgumentsMultipleConstructorsAndUnknownScopes() {
        String literalArg = """
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction">
                        <arg>42</arg>
                    </component>
                </components>
                """;
        String multipleConstructors = """
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction">
                        <arg>orderService</arg>
                    </component>
                </components>
                """;
        String unknownScope = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"
                               instance="pooled"/>
                </components>
                """;
        String action = """
                package com.example.legacy.action;
                import com.example.legacy.service.OrderService;
                public class OrderAction {
                    public OrderAction(OrderService orderService) {
                    }
                    public OrderAction(String value) {
                    }
                }
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/java/com/example/legacy/action/OrderAction.java",
                        action),
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        "package com.example.legacy.service; public class OrderService {}"));

        assertThatThrownBy(() -> rule.propose(request(literalArg, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(multipleConstructors, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(unknownScope, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void generatesQualifiedPropertyReferencesAndInitCalls() {
        String dicon = """
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction">
                        <property name="orderService">orderService</property>
                        <initMethod name="initialize"/>
                    </component>
                </components>
                """;
        String action = """
                package com.example.legacy.action;
                import com.example.legacy.service.OrderService;
                public class OrderAction {
                    public OrderService orderService;
                    public void initialize() {
                    }
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/action/OrderAction.java",
                        action))));

        assertThat(draft.targetRelativePath()).isEqualTo(
                "web-app/src/main/java/com/example/legacy/action/SimpleDiconConfiguration.java");
        assertThat(draft.proposedText())
                .contains("import org.springframework.beans.factory.annotation.Qualifier;")
                .contains("public com.example.legacy.action.OrderAction orderAction("
                        + "@Qualifier(\"orderService\")"
                        + " com.example.legacy.service.OrderService orderService)")
                .contains("bean.orderService = orderService;")
                .contains("bean.initialize();")
                .contains("return bean;");
        rule.validate(draft.proposedText(), draft.targetRelativePath());
    }

    @Test
    void usesAPublicSetterWhenNoPublicFieldExists() {
        String dicon = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService">
                        <property name="orderDao">orderDao</property>
                    </component>
                </components>
                """;
        String service = """
                package com.example.legacy.service;
                import com.example.legacy.dao.OrderDao;
                public class OrderService {
                    private OrderDao orderDao;
                    public void setOrderDao(OrderDao orderDao) {
                        this.orderDao = orderDao;
                    }
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        service))));

        assertThat(draft.proposedText())
                .contains("@Qualifier(\"orderDao\") com.example.legacy.dao.OrderDao orderDao")
                .contains("bean.setOrderDao(orderDao);");
    }

    @Test
    void refusesPropertiesWithoutTargetsAndMissingInitMethods() {
        String property = """
                <components>
                    <component name="orderService" class="com.example.legacy.service.OrderService">
                        <property name="orderDao">orderDao</property>
                    </component>
                </components>
                """;
        String lifecycle = """
                <components>
                    <component name="orderService" class="com.example.legacy.service.OrderService">
                        <initMethod name="initialize"/>
                    </component>
                </components>
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        "package com.example.legacy.service; public class OrderService {}"),
                companion("web-app/src/main/java/com/example/legacy/dao/OrderQueryDao.java",
                        "package com.example.legacy.dao; public class OrderQueryDao {}"));

        assertThatThrownBy(() -> rule.propose(request(property, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(lifecycle, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void generatesNestedAnonymousComponentsAsSeparateBeans() {
        String dicon = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService">
                        <property name="orderDao">
                            <component class="com.example.legacy.dao.OrderDao">
                                <initMethod name="initialize"/>
                            </component>
                        </property>
                    </component>
                </components>
                """;
        String service = """
                package com.example.legacy.service;
                import com.example.legacy.dao.OrderDao;
                public class OrderService {
                    public OrderDao orderDao;
                }
                """;
        String dao = """
                package com.example.legacy.dao;
                public class OrderDao {
                    public void initialize() {
                    }
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        service),
                companion("web-app/src/main/java/com/example/legacy/dao/OrderDao.java", dao))));

        String text = draft.proposedText();
        assertThat(draft.targetRelativePath()).isEqualTo(
                "web-app/src/main/java/com/example/legacy/SimpleDiconConfiguration.java");
        assertThat(text)
                .contains("public com.example.legacy.dao.OrderDao orderServiceOrderDao()")
                .contains("bean.initialize();")
                .contains("public com.example.legacy.service.OrderService orderService("
                        + "@Qualifier(\"orderServiceOrderDao\")"
                        + " com.example.legacy.dao.OrderDao orderServiceOrderDao)")
                .contains("bean.orderDao = orderServiceOrderDao;");
        assertThat(text.indexOf("orderServiceOrderDao()"))
                .isLessThan(text.indexOf("orderService("));
        rule.validate(text, draft.targetRelativePath());
    }

    @Test
    void generatesRecursiveNestedComponentsWithArgumentsAndProperties() {
        String dicon = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService">
                        <property name="orderDao">
                            <component class="com.example.legacy.service.OrderDao">
                                <property name="settings">
                                    <component class="com.example.legacy.service.Settings">
                                        <arg>"demo"</arg>
                                        <arg>7</arg>
                                    </component>
                                </property>
                            </component>
                        </property>
                    </component>
                </components>
                """;
        String service = """
                package com.example.legacy.service;
                public class OrderService {
                    public OrderDao orderDao;
                }
                """;
        String dao = """
                package com.example.legacy.service;
                public class OrderDao {
                    public Settings settings;
                }
                """;
        String settings = """
                package com.example.legacy.service;
                public class Settings {
                    public Settings(String name, int count) {
                    }
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        service),
                companion("web-app/src/main/java/com/example/legacy/service/OrderDao.java", dao),
                companion("web-app/src/main/java/com/example/legacy/service/Settings.java",
                        settings))));

        String text = draft.proposedText();
        assertThat(text)
                .contains("public com.example.legacy.service.Settings "
                        + "orderServiceOrderDaoSettings()")
                .contains("return new com.example.legacy.service.Settings(\"demo\", 7);")
                .contains("public com.example.legacy.service.OrderDao orderServiceOrderDao("
                        + "@Qualifier(\"orderServiceOrderDaoSettings\")"
                        + " com.example.legacy.service.Settings orderServiceOrderDaoSettings)")
                .contains("bean.settings = orderServiceOrderDaoSettings;")
                .contains("public com.example.legacy.service.OrderService orderService("
                        + "@Qualifier(\"orderServiceOrderDao\")"
                        + " com.example.legacy.service.OrderDao orderServiceOrderDao)")
                .contains("bean.orderDao = orderServiceOrderDao;");
        assertThat(text.indexOf("orderServiceOrderDaoSettings()"))
                .isLessThan(text.indexOf("orderServiceOrderDao("));
        assertThat(text.indexOf("orderServiceOrderDao("))
                .isLessThan(text.indexOf("orderService("));
        rule.validate(text, draft.targetRelativePath());
    }

    @Test
    void refusesInvalidNestedComponents() {
        String header = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService">
                        <property name="orderDao">
                """;
        String footer = """
                        </property>
                    </component>
                </components>
                """;
        String missingClass = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService">
                        <property name="orderDao">
                            <component class="com.example.legacy.dao.MissingDao"/>
                        </property>
                    </component>
                </components>
                """;
        String duplicateName = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService">
                        <property name="orderDao">
                            <component class="com.example.legacy.service.OrderDao"/>
                        </property>
                    </component>
                    <component name="orderServiceOrderDao"
                               class="com.example.legacy.service.OrderDao"/>
                </components>
                """;
        String wrongType = header + """
                            <component class="com.example.legacy.service.OrderDao"/>
                """ + footer;
        String mismatchService = """
                package com.example.legacy.service;
                public class OrderService {
                    public String orderDao;
                }
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        """
                        package com.example.legacy.service;
                        public class OrderService {
                            public com.example.legacy.service.OrderDao orderDao;
                        }
                        """),
                companion("web-app/src/main/java/com/example/legacy/service/OrderDao.java",
                        "package com.example.legacy.service; public class OrderDao {}"));

        assertThatThrownBy(() -> rule.propose(request(header
                + "<component name=\"nested\" class=\"com.example.legacy.service.OrderDao\"/>"
                + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.service.OrderDao\""
                + " instance=\"prototype\"/>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.service.OrderDao\""
                + " autoBinding=\"auto\"/>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "extra<component class=\"com.example.legacy.service.OrderDao\"/>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<![CDATA[extra]]>"
                + "<component class=\"com.example.legacy.service.OrderDao\"/>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.service.OrderDao\"/>"
                + "<component class=\"com.example.legacy.service.OrderDao\"/>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header + "<other/>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(missingClass, companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("not a project Java source");
        assertThatThrownBy(() -> rule.propose(request(duplicateName, companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("Duplicate component name");
        assertThatThrownBy(() -> rule.propose(request(wrongType, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        mismatchService),
                companion("web-app/src/main/java/com/example/legacy/service/OrderDao.java",
                        "package com.example.legacy.service; public class OrderDao {}")))))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.service.OrderDao\">"
                + "<aspect/></component>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void generatesNestedConstructorArgumentComponents() {
        String dicon = """
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction">
                        <arg>"demo"</arg>
                        <arg>
                            <component class="com.example.legacy.dao.OrderDao">
                                <initMethod name="initialize"/>
                            </component>
                        </arg>
                    </component>
                </components>
                """;
        String action = """
                package com.example.legacy.action;
                import com.example.legacy.dao.OrderDao;
                public class OrderAction {
                    public OrderAction(String name, OrderDao orderDao) {
                    }
                }
                """;
        String dao = """
                package com.example.legacy.dao;
                public class OrderDao {
                    public void initialize() {
                    }
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/action/OrderAction.java",
                        action),
                companion("web-app/src/main/java/com/example/legacy/dao/OrderDao.java", dao))));

        String text = draft.proposedText();
        assertThat(draft.targetRelativePath()).isEqualTo(
                "web-app/src/main/java/com/example/legacy/SimpleDiconConfiguration.java");
        assertThat(text)
                .contains("public com.example.legacy.dao.OrderDao orderActionArg2()")
                .contains("bean.initialize();")
                .contains("public com.example.legacy.action.OrderAction orderAction("
                        + "@Qualifier(\"orderActionArg2\")"
                        + " com.example.legacy.dao.OrderDao orderActionArg2)")
                .contains("return new com.example.legacy.action.OrderAction("
                        + "\"demo\", orderActionArg2);");
        assertThat(text.indexOf("orderActionArg2()"))
                .isLessThan(text.indexOf("orderAction("));
        rule.validate(text, draft.targetRelativePath());
    }

    @Test
    void generatesRecursiveNestedConstructorArguments() {
        String dicon = """
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.service.OrderAction">
                        <arg>
                            <component class="com.example.legacy.service.OrderService">
                                <arg>
                                    <component class="com.example.legacy.service.OrderDao"/>
                                </arg>
                            </component>
                        </arg>
                    </component>
                </components>
                """;
        String action = """
                package com.example.legacy.service;
                public class OrderAction {
                    public OrderAction(OrderService orderService) {
                    }
                }
                """;
        String service = """
                package com.example.legacy.service;
                public class OrderService {
                    public OrderService(OrderDao orderDao) {
                    }
                }
                """;
        String dao = """
                package com.example.legacy.service;
                public class OrderDao {
                }
                """;

        TransformationDraft draft = rule.propose(request(dicon, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderAction.java",
                        action),
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        service),
                companion("web-app/src/main/java/com/example/legacy/service/OrderDao.java",
                        dao))));

        String text = draft.proposedText();
        assertThat(text)
                .contains("public com.example.legacy.service.OrderDao orderActionArg1Arg1()")
                .contains("public com.example.legacy.service.OrderService orderActionArg1("
                        + "@Qualifier(\"orderActionArg1Arg1\")"
                        + " com.example.legacy.service.OrderDao orderActionArg1Arg1)")
                .contains("return new com.example.legacy.service.OrderService("
                        + "orderActionArg1Arg1);")
                .contains("public com.example.legacy.service.OrderAction orderAction("
                        + "@Qualifier(\"orderActionArg1\")"
                        + " com.example.legacy.service.OrderService orderActionArg1)")
                .contains("return new com.example.legacy.service.OrderAction(orderActionArg1);");
        assertThat(text.indexOf("orderActionArg1Arg1()"))
                .isLessThan(text.indexOf("orderActionArg1("));
        assertThat(text.indexOf("orderActionArg1("))
                .isLessThan(text.indexOf("orderAction("));
        rule.validate(text, draft.targetRelativePath());
    }

    @Test
    void refusesInvalidNestedConstructorArguments() {
        String header = """
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction">
                        <arg>
                """;
        String footer = """
                        </arg>
                    </component>
                </components>
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/java/com/example/legacy/action/OrderAction.java",
                        """
                        package com.example.legacy.action;
                        public class OrderAction {
                            public OrderAction(String value) {
                            }
                        }
                        """),
                companion("web-app/src/main/java/com/example/legacy/dao/OrderDao.java",
                        "package com.example.legacy.dao; public class OrderDao {}"));

        assertThatThrownBy(() -> rule.propose(request(header
                + "<component name=\"nested\" class=\"com.example.legacy.dao.OrderDao\"/>"
                + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.dao.OrderDao\""
                + " instance=\"prototype\"/>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.dao.OrderDao\""
                + " autoBinding=\"auto\"/>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "extra<component class=\"com.example.legacy.dao.OrderDao\"/>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.dao.OrderDao\"/>"
                + "<component class=\"com.example.legacy.dao.OrderDao\"/>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header + "<other/>" + footer, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.dao.MissingDao\"/>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("not a project Java source");
        assertThatThrownBy(() -> rule.propose(request(header
                + "<component class=\"com.example.legacy.dao.OrderDao\"/>" + footer,
                companions)))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> rule.propose(request("""
                <components>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction">
                        <arg><component class="com.example.legacy.dao.OrderDao"/></arg>
                    </component>
                    <component name="orderActionArg1"
                               class="com.example.legacy.dao.OrderDao"/>
                </components>
                """, List.of(
                companion("web-app/src/main/java/com/example/legacy/action/OrderAction.java",
                        """
                        package com.example.legacy.action;
                        import com.example.legacy.dao.OrderDao;
                        public class OrderAction {
                            public OrderAction(OrderDao orderDao) {
                            }
                        }
                        """),
                companion("web-app/src/main/java/com/example/legacy/dao/OrderDao.java",
                        "package com.example.legacy.dao; public class OrderDao {}")))))
                .isInstanceOf(TransformationUnsupportedException.class)
                .hasMessageContaining("Duplicate component name");
    }

    @Test
    void refusesUnsupportedScopesAttributesAndMissingIncludes() {
        String requestScoped = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"
                               instance="pooled"/>
                </components>
                """;
        String autoBinding = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"
                               autoBinding="auto"/>
                </components>
                """;
        String unknownAttribute = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"
                               custom="x"/>
                </components>
                """;
        String include = """
                <components>
                    <include path="other.dicon"/>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """;
        String literalProperty = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService">
                        <property name="orderService">"literal"</property>
                    </component>
                </components>
                """;
        List<TransformationCompanion> companions = List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        "package com.example.legacy.service; public class OrderService {}"));

        assertThatThrownBy(() -> rule.propose(request(requestScoped, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(autoBinding, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(unknownAttribute, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(include, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(literalProperty, companions)))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesClassesWithoutAUsableNoArgConstructor() {
        String source = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """;
        String withArgs = """
                package com.example.legacy.service;
                public class OrderService {
                    public OrderService(String value) {}
                }
                """;
        String privateConstructor = """
                package com.example.legacy.service;
                public class OrderService {
                    private OrderService() {}
                }
                """;
        String notPublic = """
                package com.example.legacy.service;
                class OrderService {
                }
                """;
        String missing = """
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.UnknownService"/>
                </components>
                """;

        assertThatThrownBy(() -> rule.propose(request(source, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        withArgs)))))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(source, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        privateConstructor)))))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(source, List.of(
                companion("web-app/src/main/java/com/example/legacy/service/OrderService.java",
                        notPublic)))))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(request(missing, List.of())))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void supportsOnlyDiconConfigurationItems() {
        MigrationItem configuration = item(MigrationItemType.CONFIGURATION,
                MigrationTarget.SPRING_BOOT_CONFIG);
        ProjectFile dicon = file("app.dicon", FileType.DICON);
        ProjectFile xml = file("web.xml", FileType.XML);
        MigrationItem action = item(MigrationItemType.ACTION_CONTROLLER,
                MigrationTarget.SPRING_MVC);

        assertThat(rule.supports(configuration, dicon)).isTrue();
        assertThat(rule.supports(configuration, xml)).isFalse();
        assertThat(rule.supports(action, dicon)).isFalse();
    }

    private TransformationRequest request(String dicon, List<TransformationCompanion> companions) {
        return new TransformationRequest(
                item(MigrationItemType.CONFIGURATION, MigrationTarget.SPRING_BOOT_CONFIG),
                file("simple.dicon", FileType.DICON), dicon,
                "web-app/src/main/webapp/WEB-INF/dicon/simple.dicon", List.of(), companions);
    }

    private TransformationRequest includeRequest(String path, String dicon,
                                                 List<TransformationCompanion> companions) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        ProjectFile projectFile = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), path, fileName, FileType.DICON, 10, null, "hash", false);
        return new TransformationRequest(
                item(MigrationItemType.CONFIGURATION, MigrationTarget.SPRING_BOOT_CONFIG),
                projectFile, dicon, path, List.of(), companions);
    }

    private TransformationCompanion companion(String relativePath, String source) {
        return new TransformationCompanion(relativePath, source, "hash", "UTF-8");
    }

    private ProjectFile file(String name, FileType type) {
        return ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "web-app/src/main/webapp/WEB-INF/dicon/" + name, name, type, 10, null,
                "hash", false);
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "simple.dicon", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM, MigrationItemStatus.READY,
                "TEST");
    }
}
