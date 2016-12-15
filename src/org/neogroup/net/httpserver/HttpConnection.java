
package org.neogroup.net.httpserver;

import java.nio.channels.SocketChannel;

public class HttpConnection {

    private final SocketChannel channel;

    public HttpConnection(SocketChannel channel) {
        this.channel = channel;
    }

    public SocketChannel getChannel() {
        return channel;
    }
}