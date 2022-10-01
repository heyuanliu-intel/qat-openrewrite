package org.openrewrite.qat;

import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;

public class Test {

    public static void main() throws NoSuchAlgorithmException {
        SSLContext ctx = SSLContext.getInstance(System.getProperty("ssl.protocol"));
    }
}
