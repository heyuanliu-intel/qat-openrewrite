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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.cleanup.UnnecessaryCatch;
import org.openrewrite.java.cleanup.UnnecessaryThrows;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;

import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = true)
public class SSLContextQATRecipe extends Recipe {

    private static final MethodMatcher GET_INSTANCE = new MethodMatcher("javax.net.ssl.SSLContext getInstance(..)");

    private static final MethodMatcher OPENSSL_REGISTER = new MethodMatcher("org.wildfly.openssl.OpenSSLProvider register()");

    /**
     * A method pattern that is used to find matching method for application
     * startup.
     * See {@link MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Start Up Method pattern",
            description = "A method pattern that is used to find application startup.",
            example = "com.yourorg.A foo(int, int)")
    String methodPattern;

    @Override
    public String getDisplayName() {
        return "Use system property to replace hard-coded protocol and provider.";
    }

    @Override
    public String getDescription() {
        return "Makes the SSL protocol and provider configurable, and different SSL protocols and providers can be configured at runtime.";
    }

    // getApplicableTest() is to determine if a recipe should run on any files in the source set
    //     When getApplicableTest() returns a positive result for any source file, the recipe gets to run on all files in the source set
    // Usually getSingleSourceApplicableTest() is what you want, as it operates on a per-source file basis
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate registerProvider = JavaTemplate.builder(this::getCursor,
                            "OpenSSLProvider.register()")
                    // Parser used by JavaTemplate must be aware of types within the template
                    .javaParser(() -> JavaParser.fromJavaVersion()
                            // Added a runtime dependency on this module in the build.gradle.kts so that it can be looked up from the runtime classpath
                            .classpath("wildfly-openssl-java")
                            .build())
                    // Have to provide imports referenced in within the snippet for anything not already known to be imported within the file
                    .imports("org.wildfly.openssl.OpenSSLProvider")
                    .build();

            // JavaTemplate has java standard library on the classpath by default
            // System is under java.lang, so no need to provide imports or classpath entries
            private final JavaTemplate getInstanceWithSysProperty = JavaTemplate.builder(this::getCursor,
                            "System.getProperty(\"ssl.protocol\")")
                    .build();

            private final MethodMatcher methodMatcher = new MethodMatcher(methodPattern);

            @Override
            public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!methodMatcher.matches(method.getMethodType()) || method.getBody() == null) {
                    return m;
                }

                Cursor parent = getCursor().getParent();
                assert parent != null; // Only a J.CompilationUnit would have null parent, so this is safe
                // Using doNext() will apply the recipe to the entire source file
                // Directly invoking the visitor on the relevant subtree keeps changes constrained to just that subtree
                // This makes diffs easier to code review and repository maintainers more likely to accept pull requests
                m = (J.MethodDeclaration) new UnnecessaryThrows().getVisitor().visitNonNull(m, ctx, parent);
                m = (J.MethodDeclaration) new UnnecessaryCatch().getVisitor().visitNonNull(m, ctx, parent);
                maybeRemoveImport("java.security.NoSuchProviderException");

                // Important for recipes to avoid making unnecessary changes, as recipes may run on code where no changes are required
                // Failing to reject making unnecessary changes often results in duplicate code fragments being added by the recipe
                if(containsOpenSSLRegisterInvocation(method)) {
                    return m;
                }
                maybeAddImport("org.wildfly.openssl.OpenSSLProvider");
                m =  m.withTemplate(registerProvider, method.getBody().getCoordinates().firstStatement());

                return m;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!GET_INSTANCE.matches(method)) {
                    return m;
                }
                m = m.withTemplate(getInstanceWithSysProperty, method.getCoordinates().replaceArguments());

                return m;
            }
        };
    }

    private static boolean containsOpenSSLRegisterInvocation(J j) {
        AtomicBoolean found = new AtomicBoolean();
        new ContainsOpenSSLRegisterInvocation().visit(j, found);
        return found.get();
    }

    private static class ContainsOpenSSLRegisterInvocation extends JavaIsoVisitor<AtomicBoolean> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
            if(found.get()) {
                return method;
            }
            found.set(OPENSSL_REGISTER.matches(method));
            return super.visitMethodInvocation(method, found);
        }
    }
}
