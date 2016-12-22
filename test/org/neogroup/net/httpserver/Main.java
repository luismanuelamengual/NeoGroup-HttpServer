
package org.neogroup.net.httpserver;

import org.neogroup.net.sunserver.HttpExchange;
import org.neogroup.net.sunserver.HttpHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {

    public static final String WELCOME_PHRASE = "Hi {1}, hello world {0} !!";

    public static void main(String[] args) {

        try {
            org.neogroup.net.sunserver.HttpServer server = org.neogroup.net.sunserver.HttpServer.create(new InetSocketAddress(1409), 0);
            server.createContext("/test", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {

                    exchange.sendResponseHeaders(200, WELCOME_PHRASE.length());
                    exchange.getResponseBody().write(WELCOME_PHRASE.getBytes());
                }
            });
            server.setExecutor(null);
            server.start();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        HttpServer server = new HttpServer(1408);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }
}