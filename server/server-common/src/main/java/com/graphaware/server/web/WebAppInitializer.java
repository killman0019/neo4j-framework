/*
 * Copyright (c) 2013 GraphAware
 *
 * This file is part of GraphAware.
 *
 * GraphAware is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.server.web;

import org.neo4j.graphdb.GraphDatabaseService;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.support.AbstractDispatcherServletInitializer;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

/**
 * Servlet 3.0+ web application initializer, no need for XML.
 */
public class WebAppInitializer extends AbstractDispatcherServletInitializer {

    private GraphDatabaseService database;

    public WebAppInitializer(GraphDatabaseService database) {
        this.database = database;
    }

    @Override
    protected WebApplicationContext createServletApplicationContext() {
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.getBeanFactory().registerSingleton("database", database);
        parent.refresh();

        AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
        appContext.setParent(parent);
        appContext.register(AppConfig.class);

        return appContext;
    }

    @Override
    protected String getServletName() {
        return "graphaware";
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }

    @Override
    protected WebApplicationContext createRootApplicationContext() {
        return null;
    }
}
