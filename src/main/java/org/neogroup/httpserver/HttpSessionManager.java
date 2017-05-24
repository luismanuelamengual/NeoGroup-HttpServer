
package org.neogroup.httpserver;

/**
 * Manager of session in charge of createing, obtaining and destroying sessions
 */
public interface HttpSessionManager {

    /**
     * Creates a session for a connection
     * @param connection connection
     * @return http session
     */
    public abstract HttpSession createSession (HttpConnection connection);

    /**
     * Destroy a session for a connection
     * @param connection connection
     * @return http session
     */
    public abstract HttpSession destroySession (HttpConnection connection);

    /**
     * Get a session for the connection
     * @param connection connection
     * @return http session
     */
    public abstract HttpSession getSession (HttpConnection connection);
}
