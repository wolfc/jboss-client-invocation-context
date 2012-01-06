/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
 */
package org.jboss.client.invocation.context.simple;

import org.jboss.client.invocation.context.ClientInvocationContext;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class SimpleTestCase {
    @Before
    public void before() {
        OtherBean.BARRIER.reset();
    }

    @Test
    public void test1() throws Exception {
        final OtherLocal bean = new AsyncContainer(new OtherBean()).lookup(OtherLocal.class);

        final Future<Void> future = ClientInvocationContext.invoke(bean).with(ClientInterface.class).doSomethingElse();
        assertNotNull(future);
        OtherBean.BARRIER.await(10, SECONDS);
        future.get(10, SECONDS);
    }

    @Test
    public void testAsync() throws Exception {
        final OtherLocal bean = new AsyncContainer(new OtherBean()).lookup(OtherLocal.class);

        final Future<Void> future = bean.async();
        assertNotNull(future);
        OtherBean.BARRIER.await(10, SECONDS);
        future.get(10, SECONDS);
    }


    @Test
    public void testSayHi() throws Exception {
        final OtherLocal bean = new AsyncContainer(new OtherBean()).lookup(OtherLocal.class);

        final String result = bean.sayHi("test");
        assertEquals("Hi test", result);
    }
}
