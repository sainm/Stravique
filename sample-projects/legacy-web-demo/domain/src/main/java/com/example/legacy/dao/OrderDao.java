package com.example.legacy.dao;

import com.example.legacy.domain.Order;
import org.seasar.extension.jdbc.JdbcManager;
import org.seasar.extension.tx.TransactionAttributeType;
import org.seasar.extension.tx.annotation.TransactionAttribute;
import org.seasar.framework.container.annotation.twig.Binding;
import org.seasar.framework.container.annotation.twig.Component;

import java.util.List;

@Component
public class OrderDao {

    @Binding
    public JdbcManager jdbcManager;

    public List<Order> findActive() {
        return jdbcManager.selectBySql(Order.class,
                "SELECT * FROM orders WHERE status = ?", "ACTIVE")
                .getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void flush() {
        jdbcManager.updateBySql("UPDATE orders SET status = ? WHERE id = ?", String.class, Long.class)
                .execute("ACTIVE", 1L);
    }

    public void purge(java.sql.Date cutoff) {
        jdbcManager.updateBySql("DELETE FROM orders WHERE created < ?", java.sql.Date.class)
                .execute(cutoff);
    }
}
