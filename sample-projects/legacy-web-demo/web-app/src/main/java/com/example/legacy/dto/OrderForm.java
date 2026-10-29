package com.example.legacy.dto;

import com.example.legacy.domain.Order;
import org.apache.struts.action.ActionForm;

import java.util.ArrayList;
import java.util.List;

public class OrderForm extends ActionForm {

    public String customerName;
    public String status;
    public List<Order> orders = new ArrayList<Order>();
}
