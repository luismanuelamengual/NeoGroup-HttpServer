
package org.neogroup.httpserver;

import org.neogroup.httpserver.contexts.HttpContext;
import org.neogroup.httpserver.contexts.HttpFolderContext;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.Executors;

public class Main {

    public static final String WELCOME_PHRASE = "Hi {0}, hello world !!";

    public static void main(String[] args) {

        HttpServer server = new HttpServer();
        server.setProperty(HttpServer.PORT_PROPERTY_NAME, 1408);
        server.setExecutor(Executors.newCachedThreadPool());

        server.addContext(new HttpContext("/session/create" ) {
            @Override
            public HttpResponse onContext(HttpRequest request) {

                HttpSession session = request.createSession();
                session.setAttribute("name", "Luis");

                HttpResponse response = new HttpResponse();
                response.write("Session created !!");
                return response;
            }
        });

        server.addContext(new HttpContext("/session/show" ) {
            @Override
            public HttpResponse onContext(HttpRequest request) {

                HttpSession session = request.getSession();
                HttpResponse response = new HttpResponse();
                if (session != null) {
                    response.write("session: " + session.getAttribute("name"));
                }
                else {
                    response.write("Empty session");
                }
                return response;
            }
        });

        server.addContext(new HttpContext("/session/destroy" ) {
            @Override
            public HttpResponse onContext(HttpRequest request) {

                request.destroySession();

                HttpResponse response = new HttpResponse();
                response.write("Session destroyed !!");
                return response;
            }
        });


        server.addContext(new HttpContext("/cookie/") {
            @Override
            public HttpResponse onContext(HttpRequest request) {

                HttpResponse response = new HttpResponse();
                response.addCookie(new HttpCookie("pipo", "chippolazzill"));
                response.write("Cookies set");
                return response;
            }
        });
        server.addContext(new HttpContext("/test/") {
            @Override
            public HttpResponse onContext(HttpRequest request) {
                HttpResponse response = new HttpResponse();
                response.write(MessageFormat.format(WELCOME_PHRASE, request.getParameter("name")));
                return response;
            }
        });
        server.addContext(new HttpFolderContext("/resources/", "/home/luis/git/myproject/public/"));
        server.addContext(new HttpFolderContext("/jar/", "${classPath}/"));
        server.start();
    }
}