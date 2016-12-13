
package org.neogroup.net.httpserver;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {

    public Main() {
    }

    public static void main(String[] args) {

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(1409), 0);
            server.createContext("/test", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {

                    exchange.sendResponseHeaders(200, 12);
                    exchange.getResponseBody().write("hola mundaco".getBytes());
                }
            });
            server.setExecutor(null);
            server.start();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}