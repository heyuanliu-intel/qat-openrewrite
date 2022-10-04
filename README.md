## Automatically Migrate to Intel QAT enabled OpenSSL Solution.

This project implements a [Rewrite module](https://github.com/openrewrite/rewrite) that performs common tasks when migrating from default JDK SSL/TLS implementation to Intel QAT enabled OpenSSL Solution.

## Intel QAT enabled OpenSSL Recipes

| Recipe Name                                                    | Description                                                                                      |
|----------------------------------------------------------------| ------------------------------------------------------------------------------------------------ |
| [Add wildfly openssl as maven dependency](#AddMavenDependency) | Add wildfly openssl for a Maven build.                                                           |
| [Register OpenSSL Provider](#RegisterProvider)                 | Register OpenSSL Provider in the project startup method.                                         |
| [Migrate SSLContext getInstance API](#MigrateOpenSSLContext)   | Migrate SSLContext getInstance API from hard-coded ssl protocol and provider to system property. |

### Add wildfly openssl as Maven dependency<a name="AddMavenDependency"></a>

This recipe will add or update the latest 2.2.5.Final versions of the wildfly openssl framework for an existing Maven project.

```xml
<dependencies>
    <dependency>
        <groupId>org.wildfly.openssl</groupId>
        <artifactId>wildfly-openssl</artifactId>
        <version>2.2.5.Final</version>
    </dependency>
</dependencies>
```

### Register OpenSSL Provider<a name="RegisterProvider"></a>

This recipe will register the OpenSSLProvider in the application startup method. Should define the methodPattern to match the application startup code. For example: 
rewrite.yml

```
---
type: specs.openrewrite.org/v1beta/recipe
name: com.intel.qat-recipe
displayName: Refactor project to Intel QAT enabled OpenSSL Solution
recipeList:
  - org.openrewrite.qat.SSLContextQATRecipe:
      methodPattern: com.test.Startup Main()
```

And it will register OpenSSLProvider in the application startup code.

```
 OpenSSLProvider.register();
```

## Converting OpenSSL getInstance method from hard-coded protocol/provider to system property <a name="MigrateOpenSSLContext"></a>

Methods in the `SSLContext` classes that will be migrated:

- `getInstance(string)` --> `getInstance(System.getProperty("ssl.protocol"))`
- `getInstance(string,string)` --> `getInstance(System.getProperty("ssl.protocol"))`
- `getInstance(string,Provider)` --> `getInstance(System.getProperty("ssl.protocol"))`
