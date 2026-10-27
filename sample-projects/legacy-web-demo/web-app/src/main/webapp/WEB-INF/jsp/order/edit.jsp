<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
<!DOCTYPE html>
<html>
<body>
<html:form action="/orders/save" method="post">
    <html:errors property="customerName"/>
    <html:text property="customerName"/>
    <html:submit>Save</html:submit>
</html:form>
</body>
</html>
