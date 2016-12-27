
package org.neogroup.net.httpserver;

import java.nio.channels.SocketChannel;

public class HttpConnection {

    private final SocketChannel channel;
    private boolean closed;

    public HttpConnection(SocketChannel channel) {
        this.channel = channel;
        closed = false;
    }

    public SocketChannel getChannel() {
        return channel;
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
