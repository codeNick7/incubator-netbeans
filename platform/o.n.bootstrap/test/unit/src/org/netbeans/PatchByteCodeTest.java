/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import org.netbeans.junit.NbTestCase;
import org.openide.modules.ConstructorDelegate;
import org.openide.modules.PatchFor;
import org.openide.modules.PatchedPublic;

public class PatchByteCodeTest extends NbTestCase {

    public PatchByteCodeTest(String n) {
        super(n);
    }

    public static class C {
        static final long x = 123L; // test CONSTANT_Long, tricky!
        private C(boolean doNotCare) {}
        @PatchedPublic
        private C(int doNotCare) {}
        private void m1() {}
        @PatchedPublic
        private void m2() {}
    }
    
    public static class Superclazz {
        public int val;
    }
    
    public static class CAPI extends Superclazz {
        public int otherVal;
        
        public CAPI() {
            otherVal = 1;
        }
    }
    
    @PatchFor(CAPI.class)
    public static class CompatAPI extends Superclazz {
        @ConstructorDelegate
        protected static void createAPI(CompatAPI inst, int val2) {
            inst.val = val2;
        }
    }

    public static class CAPI2 {
        CAPI2() {}
    }

    @PatchFor(CAPI2.class)
    public static class CompatAPI2 {
        @ConstructorDelegate
        public static void createAPI(CAPI2 inst, String[] param) {
        }
    }

    public void testPatchingPublic() throws Exception {
        Class<?> c = new L().loadClass(C.class.getName());
        assertNotSame(c, C.class);
        Member m;
        m = c.getDeclaredConstructor(boolean.class);
        assertEquals(0, m.getModifiers() & Modifier.PUBLIC);
        assertEquals(Modifier.PRIVATE, m.getModifiers() & Modifier.PRIVATE);
        m = c.getDeclaredConstructor(int.class);
        assertEquals(Modifier.PUBLIC, m.getModifiers() & Modifier.PUBLIC);
        assertEquals(0, m.getModifiers() & Modifier.PRIVATE);
        m = c.getDeclaredMethod("m1");
        assertEquals(0, m.getModifiers() & Modifier.PUBLIC);
        assertEquals(Modifier.PRIVATE, m.getModifiers() & Modifier.PRIVATE);
        m = c.getDeclaredMethod("m2");
        assertEquals(Modifier.PUBLIC, m.getModifiers() & Modifier.PUBLIC);
        assertEquals(0, m.getModifiers() & Modifier.PRIVATE);
    }
    
    private static class L extends ClassLoader {

        L() {
            super(L.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(PatchByteCodeTest.class.getName() + "$C")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
                int r;
                try {
                    while ((r = in.read()) != -1) {
                        baos.write(r);
                    }
                } catch (IOException x) {
                    throw new ClassNotFoundException(name, x);
                }
                byte[] data;
                try {
                    Enumeration<URL> res = getResources("META-INF/.bytecodePatched"); // NOI18N
                    data = PatchByteCode.fromStream(res, this).apply(name, baos.toByteArray());
                } catch (IOException x) {
                    throw new ClassNotFoundException(name, x);
                }
                Class<?> c = defineClass(name, data, 0, data.length);
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            } else {
                return super.loadClass(name, resolve);
            }
        }
    }

}
