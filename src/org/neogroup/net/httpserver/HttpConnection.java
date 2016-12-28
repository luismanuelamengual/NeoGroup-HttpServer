
package org.neogroup.net.httpserver;

import java.nio.channels.SocketChannel;

public class HttpConnection {

    private final SocketChannel channel;
    private final HttpRequest request;
    private final HttpResponse response;
    private boolean closed;
    private long creationTimestamp;
    private long regsitrationTimestamp;

    public HttpConnection(SocketChannel channel) {
        this.channel = channel;
        request = new HttpRequest(this);
        response = new HttpResponse(this);
        closed = false;
        long timestamp = System.currentTimeMillis();
        creationTimestamp = timestamp;
        regsitrationTimestamp = timestamp;
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

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    public long getRegistrationTimestamp() {
        return regsitrationTimestamp;
    }

    public void setRegistrationTimestamp(long registrationTimestamp) {
        this.regsitrationTimestamp = registrationTimestamp;
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
