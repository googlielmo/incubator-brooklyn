/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.entity.webapp.tomcat;

import java.io.File;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import org.apache.brooklyn.entity.webapp.HttpsSslConfig;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.repeat.Repeater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class TomcatServerWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(TomcatServerWebAppFixtureIntegrationTest.class);
    
    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TestApplication tomcatApp = newTestApplication();
        TomcatServer tomcat = tomcatApp.createAndManageChild(EntitySpec.create(TomcatServer.class)
                .configure(TomcatServer.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT)));


        File keystoreFile;
        try {
            keystoreFile = createTemporaryKeyStore("myname", "mypass");
            keystoreFile.deleteOnExit();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }

        TestApplication tomcatHttpsApp = newTestApplication();
        TomcatServer httpsTomcat = tomcatHttpsApp.createAndManageChild(EntitySpec.create(TomcatServer.class)
                .configure(TomcatServer.ENABLED_PROTOCOLS, ImmutableSet.of("https"))
                .configure(TomcatServer.HTTPS_SSL_CONFIG,
                        new HttpsSslConfig().keyAlias("myname").keystorePassword("mypass").keystoreUrl(keystoreFile.getAbsolutePath())));

        return new JavaWebAppSoftwareProcess[][] {
                new JavaWebAppSoftwareProcess[] { tomcat },
                new JavaWebAppSoftwareProcess[] { httpsTomcat }
        };
    }

    // exists to be able to test on this class from GUI in Eclipse IDE
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void canStartAndStop(final SoftwareProcess entity) {
        super.canStartAndStop(entity);
    }

    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void testReportsServiceDownWhenKilled(final SoftwareProcess entity) throws Exception {
        super.testReportsServiceDownWhenKilled(entity);
    }

    @Override
    @DataProvider(name = "entitiesWithWarAndURL")
    public Object[][] entitiesWithWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        List<Object[]> result = Lists.newArrayList();
        
        for (Object[] entity : basicEntities()) {
            result.add(new Object[] {
                    entity[0],
                    "hello-world.war",
                    "hello-world/",
                    "" // no sub-page path
                    });
        }
        return result.toArray(new Object[][] {});
    }

    @AfterMethod(alwaysRun=true, dependsOnMethods="shutdownApp")
    public void ensureIsShutDown() throws Exception {
        final AtomicReference<Socket> shutdownSocket = new AtomicReference<Socket>();
        final AtomicReference<SocketException> gotException = new AtomicReference<SocketException>();
        final Integer shutdownPort = (entity != null) ? entity.getAttribute(TomcatServer.SHUTDOWN_PORT) : null;
        
        if (shutdownPort != null) {
            boolean socketClosed = Repeater.create("Checking WebApp has shut down")
                    .repeat(new Callable<Void>() {
                            public Void call() throws Exception {
                                if (shutdownSocket.get() != null) shutdownSocket.get().close();
                                try {
                                    shutdownSocket.set(new Socket(InetAddress.getLocalHost(), shutdownPort));
                                    gotException.set(null);
                                } catch (SocketException e) {
                                    gotException.set(e);
                                }
                                return null;
                            }})
                    .every(100, TimeUnit.MILLISECONDS)
                    .until(new Callable<Boolean>() {
                            public Boolean call() {
                                return (gotException.get() != null);
                            }})
                    .limitIterationsTo(25)
                    .run();
            
            if (!socketClosed) {
                throw new Exception("Last test run did not shut down WebApp entity "+entity+" (port "+shutdownPort+")");
            }
        } else {
            Assert.fail("Cannot shutdown, because shutdown-port not set for "+entity);
        }
    }

    public static void main(String ...args) throws Exception {
        TomcatServerWebAppFixtureIntegrationTest t = new TomcatServerWebAppFixtureIntegrationTest();
        t.setUp();
        t.testReportsServiceDownWhenKilled((SoftwareProcess) t.basicEntities()[0][0]);
        t.shutdownApp();
        t.ensureIsShutDown();
        t.shutdownMgmt();
    }

}
