<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
<%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
<!DOCTYPE html>
<html>
<body>
<bean:message key="order.list.title"/>
<html:form action="/orders/save" method="post">
    <html:errors property="customerName"/>
    <html:text property="customerName"/>
    <html:submit>Save</html:submit>
</html:form>
</body>
</html>
