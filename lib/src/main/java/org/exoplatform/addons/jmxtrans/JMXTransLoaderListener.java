/*
 * Copyright (C) 2014 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */
package org.exoplatform.addons.jmxtrans;

import java.lang.management.ManagementFactory;
import java.util.List;


import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.jmxtrans.embedded.EmbeddedJmxTrans;
import org.jmxtrans.embedded.EmbeddedJmxTransException;
import org.jmxtrans.embedded.config.ConfigurationParser;
import org.jmxtrans.embedded.util.StringUtils2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap listener to start up and shut down {@link org.jmxtrans.embedded.EmbeddedJmxTrans}.
 */
public class JMXTransLoaderListener implements ServletContextListener {

    /**
     * Config param for the embedded-jmxtrans configuration urls.
     */
    public static final String CONFIG_LOCATION_PARAM = "jmxtrans.config";
    public static final String SYSTEM_CONFIG_LOCATION_PARAM = "jmxtrans.system.config";
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private EmbeddedJmxTrans embeddedJmxTrans;
    private ObjectName objectName;
    private MBeanServer mbeanServer;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().log("Start embedded-jmxtrans ...");

        mbeanServer = ManagementFactory.getPlatformMBeanServer();

        ConfigurationParser configurationParser = new ConfigurationParser();

        String configuration = configureFromSystemProperty(sce);
        if (configuration == null || configuration.isEmpty()) {
            configuration = configureFromWebXmlParam(sce);
            if (configuration == null || configuration.isEmpty()) {
                configuration = "classpath:jmxtrans.json, classpath:org/jmxtrans/embedded/config/jmxtrans-internals.json";
            }
        }

        sce.getServletContext().log("Embedded JMXTrans configuration : " + configuration);

        List<String> configurationUrls = StringUtils2.delimitedStringToList(configuration);
        embeddedJmxTrans = configurationParser.newEmbeddedJmxTrans(configurationUrls);
        String on = "org.jmxtrans.embedded:type=EmbeddedJmxTrans,name=jmxtrans,path=" + sce.getServletContext().getContextPath();
        try {
            objectName = mbeanServer.registerMBean(embeddedJmxTrans, new ObjectName(on)).getObjectName();
        } catch (Exception e) {
            throw new EmbeddedJmxTransException("Exception registering '" + objectName + "'", e);
        }
        try {
            embeddedJmxTrans.start();
        } catch (Exception e) {
            String message = "Exception starting jmxtrans for application '" + sce.getServletContext().getContextPath() + "'";
            sce.getServletContext().log(message, e);
            throw new EmbeddedJmxTransException(message, e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        sce.getServletContext().log("Stop embedded-jmxtrans ...");

        try {
            mbeanServer.unregisterMBean(objectName);
        } catch (Exception e) {
            logger.error("Silently skip exception unregistering mbean '" + objectName + "'");
        }
        try {
            embeddedJmxTrans.stop();
        } catch (Exception e) {
            throw new EmbeddedJmxTransException("Exception stopping '" + objectName + "'", e);
        }
    }

    private String configureFromSystemProperty(ServletContextEvent sce) {
        String configSystemProperty =
                sce.getServletContext().getInitParameter(SYSTEM_CONFIG_LOCATION_PARAM);

        if (configSystemProperty == null || configSystemProperty.isEmpty()) {
            return null;
        }

        String prop = System.getProperty(configSystemProperty);
        if (prop == null || prop.isEmpty()) {
            return null;
        }

        return prop;
    }

    private String configureFromWebXmlParam(ServletContextEvent sce) {
        return sce.getServletContext().getInitParameter(CONFIG_LOCATION_PARAM);
    }
}
