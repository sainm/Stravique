package dev.stravique.inventory;

import dev.stravique.domain.FileType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationInventoryAnalyzerTest {

    private final ConfigurationInventoryAnalyzer analyzer =
            new ConfigurationInventoryAnalyzer(new FindingFactory());

    @Test
    void capturesDiconComponentsScopesInjectionLifecycleAndAop() {
        String dicon = """
                <?xml version="1.0" encoding="UTF-8"?>
                <components>
                    <include path="main.dicon"/>
                    <component name="orderService" class="com.example.OrderService" instance="singleton" autoBinding="auto">
                        <arg>orderDao</arg>
                        <property name="orderDao">orderDao</property>
                        <initMethod name="initialize"/>
                        <aspect pointcut="save">traceInterceptor</aspect>
                        <interceptor name="trace"/>
                    </component>
                </components>
                """;
        TechEvidenceLedger ledger = new TechEvidenceLedger();

        AnalysisResult result = analyzer.analyze(context(
                "web-app/src/main/resources/config/components.dicon", FileType.DICON, dicon, ledger));

        assertThat(result.parseStatus().name()).isEqualTo("PARSED");
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("DICON-INCLUDE-001")
                        && finding.getLineStart() == 3
                        && finding.getMetadata().contains("main.dicon"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("DICON-COMPONENT-001")
                        && finding.getLineStart() == 4
                        && finding.getMetadata().contains("orderService")
                        && finding.getMetadata().contains("\"instance\":\"singleton\"")
                        && finding.getMetadata().contains("\"autoBinding\":\"auto\""));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("DICON-ARG-001")
                        && finding.getLineStart() == 5);
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("DICON-PROPERTY-001")
                        && finding.getLineStart() == 6
                        && finding.getMetadata().contains("orderDao"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("DICON-LIFECYCLE-001")
                        && finding.getLineStart() == 7);
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("DICON-ASPECT-001")
                        && finding.getLineStart() == 8
                        && finding.getMetadata().contains("save"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("DICON-INTERCEPTOR-001")
                        && finding.getLineStart() == 9);
        assertThat(ledger.keys()).contains("SEASAR2");
    }

    @Test
    void capturesStrutsActionMappingsForwardsMessageResourcesAndPlugins() {
        String config = """
                <?xml version="1.0" encoding="UTF-8"?>
                <struts-config>
                    <form-beans>
                        <form-bean name="orderForm" type="com.example.OrderForm"/>
                    </form-beans>
                    <message-resources parameter="messages"/>
                    <action-mappings>
                        <action path="/orders" type="com.example.OrderAction" name="orderForm" validate="true">
                            <forward name="list" path="/WEB-INF/jsp/order/list.jsp"/>
                        </action>
                    </action-mappings>
                    <plug-in className="com.example.LegacyPlugin"/>
                </struts-config>
                """;
        TechEvidenceLedger ledger = new TechEvidenceLedger();

        AnalysisResult result = analyzer.analyze(context(
                "web-app/src/main/webapp/WEB-INF/struts-config.xml", FileType.XML, config, ledger));

        assertThat(result.parseStatus().name()).isEqualTo("PARSED");
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("STRUTS-CONFIG-FORM-001")
                        && finding.getLineStart() == 4
                        && finding.getMetadata().contains("com.example.OrderForm"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("STRUTS-CONFIG-MESSAGES-001")
                        && finding.getLineStart() == 6
                        && finding.getMetadata().contains("messages"));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("STRUTS-CONFIG-ACTION-001")
                        && finding.getLineStart() == 8
                        && finding.getMetadata().contains("/orders")
                        && finding.getMetadata().contains("\"validate\":\"true\""));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("STRUTS-CONFIG-FORWARD-001")
                        && finding.getLineStart() == 9
                        && finding.getMetadata().contains("/WEB-INF/jsp/order/list.jsp")
                        && finding.getMetadata().contains("\"actionPath\":\"/orders\""));
        assertThat(result.findings())
                .anyMatch(finding -> finding.getRuleId().equals("STRUTS-CONFIG-PLUGIN-001")
                        && finding.getLineStart() == 12
                        && finding.getMetadata().contains("com.example.LegacyPlugin"));
        assertThat(ledger.keys()).contains("STRUTS1");
    }

    private AnalyzerContext context(String path, FileType type, String text,
                                    TechEvidenceLedger ledger) {
        return new AnalyzerContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                path, type, text, "test-analyzer", ledger
        );
    }
}
