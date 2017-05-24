
package org.neogroup.httpserver;

import java.nio.channels.SocketChannel;

/**
 * Class that holds all the information of a connection
 */
public class HttpConnection {

    private final HttpServer server;
    private final SocketChannel channel;
    private final HttpExchange exchange;
    private boolean closed;
    private long creationTimestamp;
    private long lastActivityTimestamp;

    /**
     * Constructor for a connection
     * @param server http server associated with the connection
     * @param channel socket channel associated with the connection
     */
    protected HttpConnection(HttpServer server, SocketChannel channel) {
        this.server = server;
        this.channel = channel;
        this.exchange = new HttpExchange(this);
        closed = false;
        long timestamp = System.currentTimeMillis();
        creationTimestamp = timestamp;
        lastActivityTimestamp = timestamp;
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
     * Creates a new http exchanges
     * @return http exchange
     */
    public HttpExchange createExchange () {
        lastActivityTimestamp = System.currentTimeMillis();
        exchange.startNewExchange();
        return exchange;
    }

    /**
     * Obtains the http exchange
     * @return http exchange
     */
    public HttpExchange getExchange() {
        return exchange;
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
    public long getLastActivityTimestamp() {
        return lastActivityTimestamp;
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
