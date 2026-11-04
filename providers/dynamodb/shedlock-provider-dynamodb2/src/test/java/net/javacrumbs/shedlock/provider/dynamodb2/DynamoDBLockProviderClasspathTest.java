/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.dynamodb2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

class DynamoDBLockProviderClasspathTest {
    @Test
    void shouldAllowProviderIntrospectionWithoutEnhancedClient() throws Exception {
        String providerPackage = DynamoDBLockProvider.class.getPackageName() + ".";
        String enhancedPackage = "software.amazon.awssdk.enhanced.dynamodb.";
        // Load provider classes separately so they resolve dependencies through the filtered loader.
        ClassLoader parent = new ClassLoader(DynamoDBLockProvider.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(providerPackage) || name.startsWith(enhancedPackage)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        URL classes =
                DynamoDBLockProvider.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes}, parent)) {
            assertThatThrownBy(() -> loader.loadClass(enhancedPackage + "DynamoDbTable"))
                    .isInstanceOf(ClassNotFoundException.class);

            Class<?> provider = loader.loadClass(DynamoDBLockProvider.class.getName());
            assertThat(provider.getConstructors()).hasSize(3);
            assertThatCode(provider::getDeclaredMethods).doesNotThrowAnyException();
            assertThatCode(provider::getDeclaredFields).doesNotThrowAnyException();
        }
    }
}
