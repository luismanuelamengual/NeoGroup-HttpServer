
package org.neogroup.net.httpserver;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class HttpConnection {

    private final SocketChannel channel;
    private boolean closed;
    private boolean autoClose;
    private long creationTimestamp;
    private long regsitrationTimestamp;
    private static final Map<Long, HttpConnection> activeConnections;

    static {
        activeConnections = new HashMap<>();
    }

    public static void setActiveConnection (HttpConnection connection) {
        if (connection != null) {
            activeConnections.put(Thread.currentThread().getId(), connection);
        }
        else {
            activeConnections.remove(Thread.currentThread().getId());
        }
    }

    public static HttpConnection getActiveConnection () {
        return activeConnections.get(Thread.currentThread().getId());
    }

    public HttpConnection(SocketChannel channel) {
        this.channel = channel;
        closed = false;
        long timestamp = System.currentTimeMillis();
        creationTimestamp = timestamp;
        regsitrationTimestamp = timestamp;
    }

    public SocketChannel getChannel() {
        return channel;
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

    public boolean isAutoClose() {
        return autoClose;
    }

    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
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

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("Connection[");
        str.append(channel.socket().getInetAddress().getHostAddress());
        str.append(":");
        str.append(channel.socket().getPort());
        str.append("]");
        return str.toString();
    }
}
