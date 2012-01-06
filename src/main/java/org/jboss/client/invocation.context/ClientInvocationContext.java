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
package org.jboss.client.invocation.context;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedList;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class ClientInvocationContext implements AnnotatedElement {
    private static final ThreadLocal<LinkedList<ClientInvocationContext>> STACK = new ThreadLocal<LinkedList<ClientInvocationContext>>();
    private static final ClientInvocationContext NONE = new ClientInvocationContext();
    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

    private final LinkedHashMap<Class<? extends Annotation>, Annotation> annotations = new LinkedHashMap<Class<? extends Annotation>, Annotation>();

    private ClientInvocationContext() {
        // do not instantiate from the outside world
    }

    public static ClientInvocationContext current() {
        final ClientInvocationContext current = stack().peek();
        if (current == null)
            return NONE;
        return current;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return (T) annotations.get(annotationClass);
    }

    public Annotation[] getAnnotations() {
        return annotations.values().toArray(EMPTY_ANNOTATION_ARRAY);
    }

    public Annotation[] getDeclaredAnnotations() {
        return annotations.values().toArray(EMPTY_ANNOTATION_ARRAY);
    }

    public static ClientObjectReference invoke(final Object obj) {
        if (obj == null)
            throw new NullPointerException("obj is null");
        return new ClientObjectReference() {
            public <T> T with(Class<T> clientView) {
                final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                final Class<?>[] interfaces = { clientView };
                final InvocationHandler handler = new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("toString") && method.getParameterTypes().length == 0)
                            return toProxyString();
                        // How we propagate context is vendor specific. This only works for
                        // thread local propagation, not for remote.
                        // Note that the client view class might not be available server side.
                        push(method.getAnnotations());
                        try {
                            final Method realMethod = obj.getClass().getMethod(method.getName(), method.getParameterTypes());
                            try {
                                // Invoking through reflection will eat away the Future returned by the container.
                                // So we must invoke though other means. The use of InvocationHandler is just a temporary hack.
                                if (Proxy.isProxyClass(obj.getClass())) {
                                    final Method intfMethod = obj.getClass().getInterfaces()[0].getMethod(method.getName(), method.getParameterTypes());
                                    return Proxy.getInvocationHandler(obj).invoke(obj, intfMethod, args);
                                }
                                return realMethod.invoke(obj, args);
                            } catch (InvocationTargetException e) {
                                final Throwable cause = e.getTargetException();
                                throw cause;
                            }
                        } finally {
                            pop();
                        }
                    }

                    private String toProxyString() {
                        return "Proxy on " + obj;
                    }
                };
                return clientView.cast(Proxy.newProxyInstance(loader, interfaces, handler));
            }
        };
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    public static void pop() {
        stack().pop();
    }

    public static void push(final Annotation[] annotations) {
        final ClientInvocationContext context = new ClientInvocationContext();
        for (Annotation annotation : annotations)
            context.annotations.put(annotation.annotationType(), annotation);
        stack().push(context);
    }

    private static final LinkedList<ClientInvocationContext> stack() {
        LinkedList<ClientInvocationContext> stack = STACK.get();
        if (stack != null)
            return stack;
        stack = new LinkedList<ClientInvocationContext>();
        STACK.set(stack);
        return stack;
    }
}
