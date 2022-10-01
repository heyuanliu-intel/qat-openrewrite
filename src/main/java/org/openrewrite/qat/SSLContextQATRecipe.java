/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.qat;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class SSLContextQATRecipe extends Recipe {
    private static final MethodMatcher GET_INSTANCE = new MethodMatcher("javax.net.ssl.SSLContext getInstance(..)");

    @Override
    public String getDisplayName() {
        return "Use system property to replace hard-coded protocol and provider.";
    }

    @Override
    public String getDescription() {
        return "Makes the SSL protocol and provider configurable, and different SSL protocols and providers can be configured at runtime.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesMethod<>(GET_INSTANCE));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            private final JavaTemplate getInstanceWithSysProperty = JavaTemplate.builder(this::getCursor,
                    "System.getProperty(\"ssl.provider\")")
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (GET_INSTANCE.matches(method)) {
                    return method.withTemplate(getInstanceWithSysProperty,
                            method.getCoordinates().replaceArguments());
                } else {
                    return super.visitMethodInvocation(method, executionContext);
                }
            }
        };
    }
}
