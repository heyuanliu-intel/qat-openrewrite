package org.openrewrite.qat;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SSLContextQATRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SSLContextQATRecipe("TestProvider main()"))
            .parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("wildfly-openssl-java"))
            .expectedCyclesThatMakeChanges(2);
    }

    @Test
    void registerProvider() {
        rewriteRun(
            java("""
                    public class TestProvider {
                        public static void main() {
                            System.out.println("startup");
                        }
                    }
                    """,
                """
                    import org.wildfly.openssl.OpenSSLProvider;

                    import javax.net.ssl.SSLContext;
                    
                    import java.security.NoSuchAlgorithmException;

                    public class TestProvider {
                        public static void main() {
                            OpenSSLProvider.register();
                            try {
                                SSLContext sslContext = SSLContext.getInstance(System.getProperty("ssl.protocol"));
                                SSLContext.setDefault(sslContext);
                            } catch (NoSuchAlgorithmException e) {
                            }
                            System.out.println("startup");
                        }
                    }
                    """)
        );
    }

    @Test
    void replaceSSLContext() {
        rewriteRun(
            java("""
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
                            SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.protocol"));
                        }
                    }
                    """)
        );
    }

    @Test
    void replaceSSLContextWithProvider() {
        rewriteRun(
            java("""
                    import java.security.NoSuchProviderException;
                    import java.security.NoSuchAlgorithmException;
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
                            SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.protocol"));
                        }
                    }
                    """));
    }

    @Test
    void replaceSSLContextWithProviderAndTryCatch() {
        rewriteRun(
            java("""
                    import java.security.NoSuchProviderException;
                    import java.security.NoSuchAlgorithmException;
                    import javax.net.ssl.SSLContext;

                    public class Test {
                        public static void main() throws NoSuchAlgorithmException {
                            try {
                                SSLContext ctx = SSLContext.getInstance("TLS", "qat");
                            } catch (NoSuchProviderException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    """,
                """
                    import java.security.NoSuchAlgorithmException;
                    import javax.net.ssl.SSLContext;
                    
                    public class Test {
                        public static void main() throws NoSuchAlgorithmException {
                            SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.protocol"));
                        }
                    }
                    """)
        );
    }

    @Test
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
                            SSLContext ctx = SSLContext.getInstance("TLS", new Provider(null, null, null) {});
                        }
                    }
                    """,
                """
                    import java.security.NoSuchAlgorithmException;
                    import java.security.Provider;
                    
                    import javax.net.ssl.SSLContext;
                    
                    public class Test {
                    
                        public static void main() throws NoSuchAlgorithmException {
                            SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.protocol"));
                        }
                    }
                    """)
        );
    }
}
