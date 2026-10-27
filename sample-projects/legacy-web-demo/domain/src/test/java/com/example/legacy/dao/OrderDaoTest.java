package com.example.legacy.dao;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class OrderDaoTest {

    @Test
    public void fixtureLoads() {
        assertNotNull(new OrderDao());
    }
}
