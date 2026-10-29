package com.example.legacy.dao;

import com.example.legacy.domain.Order;
import org.seasar.extension.jdbc.JdbcManager;
import org.seasar.framework.container.annotation.twig.Binding;
import org.seasar.framework.container.annotation.twig.Component;

import java.util.List;

@Component
public class OrderQueryDao {

    @Binding
    public JdbcManager jdbcManager;

    public List<Order> findByStatus(String status) {
        return jdbcManager.selectBySql(Order.class,
                "SELECT * FROM orders WHERE status = ?", status)
                .getResultList();
    }
}