
package org.neogroup.httpserver;

import org.neogroup.httpserver.contexts.HttpContext;
import org.neogroup.httpserver.contexts.HttpFolderContext;

import java.text.MessageFormat;
import java.util.concurrent.Executors;

public class Main {

    public static final String WELCOME_PHRASE = "Hi {0}, hello world !!";

    public static void main(String[] args) {

        HttpServer server = new HttpServer(1408);
        server.setExecutor(Executors.newCachedThreadPool());
        server.addContext(new HttpContext("/test/") {
            @Override
            public HttpResponse onContext(HttpRequest request) {
                HttpResponse response = new HttpResponse();
                response.write(MessageFormat.format(WELCOME_PHRASE, request.getParameter("name")));
                return response;
            }
        });
        server.addContext(new HttpFolderContext("/resources/", "/home/luis/git/sitracksite/public/"));
        server.addContext(new HttpFolderContext("/jar/", "${classPath}/"));
        server.start();
    }
}