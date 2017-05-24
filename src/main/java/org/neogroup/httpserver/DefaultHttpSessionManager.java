
package org.neogroup.httpserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation for the http sessions
 */
public class DefaultHttpSessionManager implements HttpSessionManager {

    public static final String SESSION_NAME_PROPERTY_NAME = "sessionName";
    public static final String SESSION_USE_COOKIES_PROPERTY_NAME = "sessionUseCookies";
    public static final String SESSION_MAX_INACTIVE_INTERVAL_PROPERTY_NAME = "sessionMaxInactiveInterval";

    public static final String DEFAULT_SESSION_NAME = "sessionId";

    private Map<UUID, HttpSession> sessions;

    /**
     * Constructor for the http session manager
     */
    public DefaultHttpSessionManager() {
        this.sessions = Collections.synchronizedMap(new HashMap<UUID, HttpSession>());
    }

    /**
     * Creates a session for the given connection
     * @param connection connection
     * @return created http session
     */
    @Override
    public HttpSession createSession(HttpConnection connection) {
        HttpSession session = new HttpSession();
        session.setLastActivityTimestamp(System.currentTimeMillis());
        session.setMaxInactiveInterval(connection.getServer().getProperty(SESSION_MAX_INACTIVE_INTERVAL_PROPERTY_NAME, 300000));
        sessions.put(session.getId(), session);
        if (getSessionUseCookies(connection)) {
            HttpCookie cookie = new HttpCookie(getSessionName(connection), session.getId().toString());
            connection.getExchange().addCookie(cookie);
        }
        return session;
    }

    /**
     * Destroys the session for the given connection
     * @param connection connection
     * @param session session to destroy
     * @return destroyed http session
     */
    @Override
    public HttpSession destroySession(HttpConnection connection, HttpSession session) {
        session.clearAttributes();
        sessions.remove(session.getId());
        if (getSessionUseCookies(connection)) {
            HttpCookie cookie = new HttpCookie(getSessionName(connection), "");
            cookie.setMaxAge(0);
            connection.getExchange().addCookie(cookie);
        }
        return session;
    }

    /**
     * Get a session from the given connection
     * @param connection connection
     * @return http session
     */
    @Override
    public HttpSession getSession(HttpConnection connection) {
        HttpSession session = null;
        UUID sessionId = getSessionId(connection);
        if (sessionId != null) {
            session = sessions.get(sessionId);
            if (session != null) {
                session.setLastActivityTimestamp(System.currentTimeMillis());
            }
        }
        return session;
    }

    /**
     * Obtains the session id from the connection
     * @param connection connection
     * @return id of session
     */
    protected UUID getSessionId (HttpConnection connection) {

        UUID sessionId = null;
        String sessionName = getSessionName(connection);
        if (getSessionUseCookies(connection)) {
            HttpCookie sessionCookie = connection.getExchange().getCookie(sessionName);
            if (sessionCookie != null && !sessionCookie.getValue().isEmpty()) {
                sessionId = UUID.fromString(sessionCookie.getValue());
            }
        }
        else {
            String sessionIdString = connection.getExchange().getRequestParameter(sessionName);
            if (sessionIdString != null && !sessionIdString.isEmpty()) {
                sessionId = UUID.fromString(sessionIdString);
            }
        }
        return sessionId;
    }

    /**
     * Get the session parameter name
     * @param connection connection
     * @return session name
     */
    protected String getSessionName (HttpConnection connection) {
        return connection.getServer().getProperty(SESSION_NAME_PROPERTY_NAME, DEFAULT_SESSION_NAME);
    }

    /**
     * Indicates if the session for the connection should use cookies or not
     * @param connection connection
     * @return use or not cookies
     */
    protected boolean getSessionUseCookies (HttpConnection connection) {
        return connection.getServer().getProperty(SESSION_USE_COOKIES_PROPERTY_NAME, true);
    }
}
