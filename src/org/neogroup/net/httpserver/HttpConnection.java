
package org.neogroup.net.httpserver;

import java.nio.channels.SocketChannel;

public class HttpConnection {

    private final SocketChannel channel;
    private final HttpRequest request;
    private final HttpResponse response;
    private boolean closed;

    public HttpConnection(SocketChannel channel) {
        this.channel = channel;
        request = new HttpRequest(channel);
        response = new HttpResponse(channel);
        closed = false;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public HttpRequest getRequest () {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public boolean isClosed() {
        return closed;
    }

    public synchronized void close() {

        if (!closed) {
            try {
                channel.shutdownInput();
            } catch (Exception ex) {}

            try {
                channel.shutdownOutput();
            } catch (Exception ex) {}

            try {
                channel.close();
            } catch (Exception ex) {}
            closed = true;
        }
    }
}
