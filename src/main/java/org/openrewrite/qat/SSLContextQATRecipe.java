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

import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;

public class SSLContextQATRecipe extends Recipe {

    private static final MethodMatcher GET_INSTANCE = new MethodMatcher("javax.net.ssl.SSLContext getInstance(..)");

    /**
     * A method pattern that is used to find matching method for application
     * startup.
     * See {@link MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Start Up Method pattern", description = "A method pattern that is used to find application startup.", example = "com.yourorg.A foo(int, int)")
    String methodPattern;

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
                doAfterVisit(new UsesMethod<>(methodPattern));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SSLContextVisitor(new MethodMatcher(this.methodPattern));
    }

    private static class SSLContextVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final JavaTemplate registerProvider = JavaTemplate.builder(this::getCursor,
                "OpenSSLProvider.register()")
                .build();

        private final JavaTemplate getInstanceWithSysProperty = JavaTemplate.builder(this::getCursor,
                "System.getProperty(\"ssl.protocol\")")
                .build();

        private final MethodMatcher methodMatcher;

        public SSLContextVisitor(MethodMatcher methodMatcher) {
            this.methodMatcher = methodMatcher;
        }

        @Override
        public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext ctx) {
            if (methodMatcher.matches(method.getMethodType())) {
                maybeAddImport("org.wildfly.openssl.OpenSSLProvider");
                assert method.getBody() != null;
                method = method.withTemplate(registerProvider, method.getBody().getCoordinates().firstStatement());
            }
            return super.visitMethodDeclaration(method,ctx);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (GET_INSTANCE.matches(method)) {
                return method.withTemplate(getInstanceWithSysProperty, method.getCoordinates().replaceArguments());
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
