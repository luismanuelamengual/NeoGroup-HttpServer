# NeoGroup-HttpServer

Fast Open-source HTTP server for modern operating systems. The goal of this project is to provide a secure, efficient and extensible server that provides HTTP services in sync with the current HTTP standards.

Getting started
---------------

For maven users, just add the following dependency

```xml
<dependency>
    <groupId>com.github.luismanuelamengual</groupId>
    <artifactId>NeoGroup-HttpServer</artifactId>
    <version>1.0</version>
</dependency>
```

Examples
---------

Simple "hello world" example of a http server listening at port *80*

```java
package example;

import org.neogroup.httpserver.HttpServer;
import org.neogroup.httpserver.HttpResponse;
import org.neogroup.httpserver.contexts.HttpContext;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        
        HttpServer server = new HttpServer(80);
        server.setExecutor(Executors.newCachedThreadPool());
        server.addContext(new HttpContext("/test/") {
            @Override
            public HttpResponse onContext(HttpRequest request) {
                HttpResponse response = new HttpResponse();
                response.write("Hello world !!");
                return response;
            }
        });
        server.start();
    }
}
```

Publishing classpath resources and files from a path folder

```java
package example;

import org.neogroup.httpserver.HttpServer;
import org.neogroup.httpserver.HttpResponse;
import org.neogroup.httpserver.contexts.HttpFolderContext;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        
        HttpServer server = new HttpServer(80);
        server.setExecutor(Executors.newCachedThreadPool());
        server.addContext(new HttpFolderContext("/resources/", "/home/luis/git/myproject/public/"));
        server.addContext(new HttpFolderContext("/jar/", "${classPath}/"));
        server.start();
    }
}
```


