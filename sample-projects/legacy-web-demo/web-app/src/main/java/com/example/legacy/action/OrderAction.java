package com.example.legacy.action;

import com.example.legacy.dto.OrderForm;
import com.example.legacy.framework.BaseWorkflowController;
import com.example.legacy.service.OrderService;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.seasar.framework.container.annotation.twig.Binding;
import org.seasar.struts.annotation.Execute;
import org.seasar.struts.annotation.MaxLength;
import org.seasar.struts.annotation.Required;

public class OrderAction extends BaseWorkflowController {

    @Binding
    public OrderService orderService;

    @Required
    public OrderForm orderForm;

    @Required
    @MaxLength(120)
    public String customerName;

    @Execute(validator = false)
    public String index() {
        orderForm.orders = orderService.findVisibleOrders();
        return "list.jsp";
    }

    @Execute(validator = true, input = "edit.jsp")
    public String save() {
        try {
            orderService.save(orderForm);
            return "complete.jsp";
        } catch (RuntimeException exception) {
            ActionMessages messages = new ActionMessages();
            messages.add("order", new ActionMessage("error.order.save"));
            saveMessages(messages);
            return "edit.jsp";
        }
    }

    public void loadExtension() throws Exception {
        Class<?> extension = Class.forName("com.example.legacy.ext.CustomOrderExtension");
        extension.getDeclaredMethod("activate").invoke(null);
    }
}
