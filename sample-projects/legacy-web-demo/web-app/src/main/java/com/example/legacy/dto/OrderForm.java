package com.example.legacy.dto;

import com.example.legacy.domain.Order;

import java.util.ArrayList;
import java.util.List;

public class OrderForm {

    public String customerName;
    public String status;
    public List<Order> orders = new ArrayList<Order>();
}
