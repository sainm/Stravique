<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
<%@ taglib prefix="sq" uri="http://example.com/tags/legacy-ui" %>
<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="/assets/legacy.css">
    <script src="/assets/order.js"></script>
</head>
<body>
<html:errors/>
<html:messages id="message" message="true">
    <p class="message"><c:out value="${message}"/></p>
</html:messages>
<fmt:message key="order.list.title"/>
<form action="/orders" method="post">
    <input name="customerName" value="${orderForm.customerName}">
    <button type="submit"><fmt:message key="common.search"/></button>
</form>
<table>
    <c:forEach items="${orderForm.orders}" var="order">
        <tr>
            <td><c:out value="${order.id}"/></td>
            <td><c:out value="${order.customerName}"/></td>
        </tr>
    </c:forEach>
</table>
<sq:emptyState message="orders.empty"/>
</body>
</html>
