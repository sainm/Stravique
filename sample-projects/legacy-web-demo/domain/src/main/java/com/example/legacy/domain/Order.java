package com.example.legacy.domain;

import org.seasar.extension.jdbc.name.PropertyName;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    public Long id;

    public String customerName;
    public String status;

    public static class Names {
        public static final PropertyName<Order> id = new PropertyName<Order>("id");
    }
}
