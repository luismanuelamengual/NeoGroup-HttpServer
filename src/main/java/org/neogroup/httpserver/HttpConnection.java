
package org.neogroup.httpserver;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that holds all the information of a connection
 */
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

    /**
     * Sets the active connection for the current thread
     * @param connection current thread active connection
     */
    public static void setActiveConnection (HttpConnection connection) {
        if (connection != null) {
            activeConnections.put(Thread.currentThread().getId(), connection);
        }
        else {
            activeConnections.remove(Thread.currentThread().getId());
        }
    }

    /**
     * Gets the current thread active connection
     * @return The connection for the current thread
     */
    public static HttpConnection getActiveConnection () {
        return activeConnections.get(Thread.currentThread().getId());
    }

    /**
     * Constructor for a connection
     * @param channel socket channel associated with the connection
     */
    public HttpConnection(SocketChannel channel) {
        this.channel = channel;
        closed = false;
        long timestamp = System.currentTimeMillis();
        creationTimestamp = timestamp;
        regsitrationTimestamp = timestamp;
    }

    /**
     * Obtains the socket channel associated with the connection
     * @return SocketChannel from the connection
     */
    public SocketChannel getChannel() {
        return channel;
    }

    /**
     * Indicates where the connection is closed or not
     * @return boolean the indicates if the connection is closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Obtains the connection creation timestamp
     * @return obtains a timestamp of the creation
     */
    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    /**
     * Obtains the connection registration timestamp
     * @return obtains a timestamp of the registration
     */
    public long getRegistrationTimestamp() {
        return regsitrationTimestamp;
    }

    /**
     * Sets the connections registration timestamp
     * @param registrationTimestamp timestamp of registration
     */
    public void setRegistrationTimestamp(long registrationTimestamp) {
        this.regsitrationTimestamp = registrationTimestamp;
    }

    /**
     * Indicates if the connection should auto close or not
     * @return boolean that indicates if the connection should auto close
     */
    public boolean isAutoClose() {
        return autoClose;
    }

    /**
     * Sets if the connection should auto close or not
     * @param autoClose boolean
     */
    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    /**
     * Closes the connection
     */
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

    /**
     * Obtains a representation of the connection as a string
     * @return string that represent a connection
     */
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
