package org.openrewrite.qat;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SSLContextQATRecipeTest implements RewriteTest {

    // Note, you can define defaults for the RecipeSpec and these defaults will be
    // used for all tests.
    // In this case, the recipe and the parser are common. See below, on how the
    // defaults can be overridden
    // per test.
    @Override
    public void defaults(RecipeSpec spec) {

        spec.recipe(new SSLContextQATRecipe()).parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("guava"));
    }

    @Test
    void replaceSSLContext() {
        rewriteRun(
                // There is an overloaded version or rewriteRun that allows the RecipeSpec to be
                // customized specifically
                // for a given test. In this case, the parser for this test is configured to not
                // log compilation warnings.
                spec -> spec.parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(false)
                        .classpath("guava")),
                java(
                        """
                                import java.security.NoSuchAlgorithmException;

                                import javax.net.ssl.SSLContext;

                                public class Test {
                                    public static void main() throws NoSuchAlgorithmException {
                                        SSLContext ctx = SSLContext.getInstance("TLS");
                                    }
                                }
                                """,
                        """
                                import java.security.NoSuchAlgorithmException;

                                import javax.net.ssl.SSLContext;

                                public class Test {
                                    public static void main() throws NoSuchAlgorithmException {
                                        SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.provider"));
                                    }
                                }
                                """));
    }

    void replaceSSLContextWithProvider() {
        rewriteRun(
                java(
                        """
                                import java.security.NoSuchAlgorithmException;
                                import java.security.NoSuchProviderException;

                                import javax.net.ssl.SSLContext;

                                public class Test {
                                    public static void main() throws NoSuchAlgorithmException, NoSuchProviderException {
                                        SSLContext ctx = SSLContext.getInstance("TLS", "qat");
                                    }
                                }
                                """,
                        """
                                import java.security.NoSuchAlgorithmException;

                                import javax.net.ssl.SSLContext;

                                public class Test {
                                    public static void main() throws NoSuchAlgorithmException {
                                        SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.provider"));
                                    }
                                }
                                """));
    }

    void replaceSSLContextWithProviderClass() {
        rewriteRun(
                java(
                        """
                                import java.security.NoSuchAlgorithmException;
                                import java.security.NoSuchProviderException;
                                import java.security.Provider;

                                import javax.net.ssl.SSLContext;

                                public class Test {

                                    public static void main() throws NoSuchAlgorithmException, NoSuchProviderException {
                                        SSLContext ctx = SSLContext.getInstance("TLS", new Provider(null, null, null) {

                                        });
                                    }
                                }
                                """,
                        """
                                import java.security.NoSuchAlgorithmException;

                                import javax.net.ssl.SSLContext;

                                public class Test {
                                    public static void main() throws NoSuchAlgorithmException {
                                        SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.provider"));
                                    }
                                }
                                """));
    }
}
