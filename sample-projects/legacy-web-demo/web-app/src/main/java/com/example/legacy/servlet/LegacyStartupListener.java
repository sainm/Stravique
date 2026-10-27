package com.example.legacy.servlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class LegacyStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute("inventoryVersion", "1.7");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
    }
}
