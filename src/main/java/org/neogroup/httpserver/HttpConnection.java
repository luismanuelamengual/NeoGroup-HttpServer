
package org.neogroup.httpserver;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that holds all the information of a connection
 */
public class HttpConnection {

    private final HttpServer server;
    private final SocketChannel channel;
    private HttpExchange currentExchange;
    private boolean closed;
    private long creationTimestamp;
    private long regsitrationTimestamp;

    /**
     * Constructor for a connection
     * @param server http server associated with the connection
     * @param channel socket channel associated with the connection
     */
    protected HttpConnection(HttpServer server, SocketChannel channel) {
        this.server = server;
        this.channel = channel;
        closed = false;
        long timestamp = System.currentTimeMillis();
        creationTimestamp = timestamp;
        regsitrationTimestamp = timestamp;
    }

    /**
     * Obtains the server associated with the connection
     * @return http server
     */
    public HttpServer getServer() {
        return server;
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
    protected void setRegistrationTimestamp(long registrationTimestamp) {
        this.regsitrationTimestamp = registrationTimestamp;
    }

    /**
     * Get the current exchange
     * @return http exchange
     */
    protected HttpExchange getCurrentExchange() {
        return currentExchange;
    }

    /**
     * Set the current exchange
     * @param currentExchange current exchange
     */
    protected void setCurrentExchange(HttpExchange currentExchange) {
        this.currentExchange = currentExchange;
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
