package dev.stravique.transformation;

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

class JspBeanMessageJstlRuleTest {

    private final JspBeanMessageJstlRule rule = new JspBeanMessageJstlRule();

    @Test
    void rewritesSelfClosingBeanMessagesAndAddsTheJstlTaglib() {
        String source = """
                <%@ page contentType="text/html; charset=UTF-8" %>
                <%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
                <%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
                <html>
                <body>
                <bean:message key="order.list.title"/>
                <html:form action="/orders/save">
                    <html:text property="customerName"/>
                </html:form>
                </body>
                </html>
                """;

        String proposed = rule.propose(source, "web-app/src/main/webapp/WEB-INF/jsp/order/edit.jsp");

        assertThat(proposed)
                .contains("<%@ taglib prefix=\"fmt\" uri=\"jakarta.tags.fmt\" %>")
                .contains("<fmt:message key=\"order.list.title\"/>")
                .doesNotContain("<bean:message")
                .doesNotContain("prefix=\"bean\"")
                .contains("<html:form action=\"/orders/save\">")
                .contains("<html:text property=\"customerName\"/>");
        assertThat(proposed.indexOf("jakarta.tags.fmt"))
                .isLessThan(proposed.indexOf("tags-html"));
        rule.validate(proposed);
    }

    @Test
    void reusesFmtAndConvertsBeanWriteWithANewCoreTaglib() {
        String source = """
                <%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
                <%@ taglib prefix="jstl" uri="http://java.sun.com/jsp/jstl/fmt" %>
                <bean:message key="order.list.title"/>
                <bean:write name="orderForm" property="customerName"/>
                """;

        String proposed = rule.propose(source, "order.jsp");

        assertThat(proposed)
                .contains("<jstl:message key=\"order.list.title\"/>")
                .contains("<c:out value=\"${orderForm.customerName}\"/>")
                .contains("<%@ taglib prefix=\"c\" uri=\"jakarta.tags.core\" %>")
                .contains("prefix=\"jstl\" uri=\"jakarta.tags.fmt\"")
                .doesNotContain("<bean:write")
                .doesNotContain("prefix=\"bean\"")
                .doesNotContain("java.sun.com/jsp/jstl");
    }

    @Test
    void modernizesLegacyJstlUrisAndAddsJakartaFmt() {
        String source = """
                <%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
                <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
                <bean:message key="order.list.title"/>
                """;

        String proposed = rule.propose(source, "order.jsp");

        assertThat(proposed)
                .contains("<%@ taglib prefix=\"fmt\" uri=\"jakarta.tags.fmt\" %>")
                .contains("uri=\"jakarta.tags.core\"")
                .doesNotContain("java.sun.com/jsp/jstl");
    }

    @Test
    void modernizesLegacyJstlUrisWithoutMessageTags() {
        String source = """
                <%@ page contentType="text/html; charset=UTF-8" %>
                <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
                <%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
                <html>
                <body>
                <c:out value="${order.id}"/>
                <fmt:message key="order.list.title"/>
                </body>
                </html>
                """;

        String proposed = rule.propose(source, "list.jsp");

        assertThat(proposed)
                .contains("uri=\"jakarta.tags.core\"")
                .contains("uri=\"jakarta.tags.fmt\"")
                .doesNotContain("java.sun.com/jsp/jstl")
                .contains("<c:out value=\"${order.id}\"/>")
                .contains("<fmt:message key=\"order.list.title\"/>");
        assertThat(rule.ruleVersion()).isEqualTo("1.2.0");
    }

    @Test
    void refusesUnknownLegacyJstlUrisAndRemainingUrisOnValidation() {
        String source = """
                <%@ taglib prefix="custom" uri="http://java.sun.com/jsp/jstl/custom" %>
                <custom:value/>
                """;

        assertThatThrownBy(() -> rule.propose(source, "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.validate(
                "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\" %>"))
                .isInstanceOf(TransformationInvalidException.class);
    }

    @Test
    void keepsTheBeanTaglibWhenAnExpressionUsesItsPrefix() {
        String source = """
                <%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
                <bean:message key="order.list.title"/>
                <p>${bean:resourceCode}</p>
                """;

        String proposed = rule.propose(source, "order.jsp");

        assertThat(proposed)
                .contains("<fmt:message key=\"order.list.title\"/>")
                .contains("prefix=\"bean\"")
                .contains("${bean:resourceCode}");
    }

    @Test
    void convertsBeanWriteAndReusesAnExistingCorePrefix() {
        String source = """
                <%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
                <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
                <bean:write name="orderForm" property="customerName"/>
                <bean:write name="order"/>
                """;

        String proposed = rule.propose(source, "order.jsp");

        assertThat(proposed)
                .contains("uri=\"jakarta.tags.core\"")
                .contains("<c:out value=\"${orderForm.customerName}\"/>")
                .contains("<c:out value=\"${order}\"/>")
                .doesNotContain("<bean:write")
                .doesNotContain("prefix=\"bean\"");
        assertThat(proposed.split("jakarta.tags.core", -1)).hasSize(2);
    }

    @Test
    void refusesBeanWriteFormsThatNeedManualReview() {
        String taglib = "<%@ taglib prefix=\"bean\" uri=\"http://struts.apache.org/tags-bean\" %>\n";

        assertThatThrownBy(() -> rule.propose(taglib
                + "<bean:write name=\"order\" property=\"customerName\" scope=\"session\"/>\n",
                "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(taglib
                + "<bean:write name=\"order\" property=\"customerName\" filter=\"false\"/>\n",
                "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(taglib
                + "<bean:write name=\"${order}\" property=\"customerName\"/>\n", "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(taglib
                + "<bean:write name=\"order\"></bean:write>\n", "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesUnsupportedMessageAttributesAndBodies() {
        String taglib = "<%@ taglib prefix=\"bean\" uri=\"http://struts.apache.org/tags-bean\" %>\n";

        assertThatThrownBy(() -> rule.propose(taglib
                + "<bean:message key=\"x\" arg0=\"y\"/>\n", "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose(taglib
                + "<bean:message key=\"x\"></bean:message>\n", "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
        assertThatThrownBy(() -> rule.propose("<bean:message key=\"x\"/>\n", "order.jsp"))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    @Test
    void refusesUnbalancedJspTagStructure() {
        String source = """
                <%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
                <%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
                <html:form action="/orders">
                <bean:message key="x"/>
                """;

        assertThatThrownBy(() -> rule.propose(source, "order.jsp"))
                .isInstanceOf(TransformationInvalidException.class);
        assertThatThrownBy(() -> rule.validate("<html:form>"))
                .isInstanceOf(TransformationInvalidException.class);
    }

    @Test
    void supportsOnlyJspViewsForKeepJsp() {
        ProjectFile jsp = ProjectFile.discovered(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "edit.jsp", "edit.jsp", FileType.JSP,
                10, null, "hash", false);
        MigrationItem view = item(MigrationItemType.JSP_VIEW, MigrationTarget.KEEP_JSP);
        MigrationItem action = item(MigrationItemType.ACTION_CONTROLLER, MigrationTarget.SPRING_MVC);

        assertThat(rule.supports(view, jsp)).isTrue();
        assertThat(rule.supports(action, jsp)).isFalse();
    }

    private MigrationItem item(MigrationItemType type, MigrationTarget target) {
        return MigrationItem.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item", type, target, "it", null, null, 3,
                MigrationAutomation.ASSISTED, MigrationRisk.LOW, MigrationItemStatus.READY,
                "TEST");
    }
}
