
package org.neogroup.net.httpserver;

import java.text.MessageFormat;
import java.util.concurrent.Executors;

public class Main {

    public static final String WELCOME_PHRASE = "Hi {0}, hello world !!";

    public static void main(String[] args) {

        HttpServer server = new HttpServer(1408);
        server.setExecutor(Executors.newCachedThreadPool());
        server.addContext(new HttpContext("/test/") {
            @Override
            public void onContext(HttpRequest request, HttpResponse response) {
                response.write(MessageFormat.format(WELCOME_PHRASE, request.getParameter("name")));
            }
        });
        server.addContext(new HttpResourcesContext("/resources/", "/home/luis/git/sitrackfrontend/public/"));
        server.addContext(new HttpResourcesContext("/jar_resources/", "resource:/"));
        server.start();
    }
}