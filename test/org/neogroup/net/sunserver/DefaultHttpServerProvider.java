
package org.neogroup.net.sunserver;

import org.neogroup.net.sunserver.spi.HttpServerProvider;

import java.net.*;
import java.io.*;

public class DefaultHttpServerProvider extends HttpServerProvider {
    public HttpServer createHttpServer (InetSocketAddress addr, int backlog) throws IOException {
        return new HttpServerImpl (addr, backlog);
    }

    public HttpsServer createHttpsServer (InetSocketAddress addr, int backlog) throws IOException {
        return new HttpsServerImpl (addr, backlog);
    }
}
