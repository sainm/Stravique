package com.example.legacy.service;

import com.example.legacy.dao.OrderDao;
import com.example.legacy.domain.Order;
import org.seasar.framework.container.annotation.twig.Component;
import org.seasar.framework.container.annotation.twig.Binding;

import java.util.List;

@Component(instance = "singleton")
public class OrderService {

    @Binding
    public OrderDao orderDao;

    public List<Order> findVisibleOrders() {
        return orderDao.findActive();
    }

    public void save(Object form) {
        orderDao.flush();
    }
}
