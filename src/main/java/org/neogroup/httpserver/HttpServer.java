
package org.neogroup.httpserver;

import org.neogroup.httpserver.contexts.HttpContext;
import org.neogroup.util.MimeUtils;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Http Server
 */
public class HttpServer {

    public static final String SERVER_NAME_PROPERTY_NAME = "serverName";
    public static final String PORT_PROPERTY_NAME = "port";
    public static final String LOGGING_ENABLED_PROPERTY_NAME = "loggingEnabled";
    public static final String CONNECTION_CHECKOUT_INTERVAL_PROPERTY_NAME = "connectionCheckoutInterval";
    public static final String CONNECTION_MAX_INACTIVE_INTERVAL_PROPERTY_NAME = "connectionMaxInactiveInterval";
    public static final String SESSION_NAME_PROPERTY_NAME = "sessionName";
    public static final String SESSION_USE_COOKIES_PROPERTY_NAME = "sessionUseCookies";
    public static final String SESSION_MAX_INACTIVE_INTERVAL_PROPERTY_NAME = "sessionMaxInactiveInterval";
    public static final String SESSION_CHECKOUT_INTERVAL_PROPERTY_NAME = "sessionCheckoutInterval";

    public static final int DEFAULT_PORT = 80;
    public static final boolean DEFAULT_LOGGING_ENABLED = false;
    public static final int DEFAULT_CONNECTION_MAX_INACTIVE_INTERVAL = 5000;
    public static final int DEFAULT_CONNECTION_CHECKOUT_INTERVAL = 20000;
    public static final String DEFAULT_SERVER_NAME = "NeoGroup-HttpServer";
    public static final String DEFAULT_SESSION_NAME = "sessionId";
    public static final int DEFAULT_SESSION_MAX_INACTIVE_INTERVAL = 300000;
    public static final int DEFAULT_SESSION_CHECKOUT_INTERVAL = 60000;
    public static final boolean DEFAULT_SESSION_USE_COOKIES = true;

    private static final String CONNECTION_CREATED_MESSAGE = "Connection \"{0}\" created !!";
    private static final String CONNECTION_DESTROYED_MESSAGE = "Connection \"{0}\" destroyed !!";
    private static final String CONNECTION_REQUEST_RECEIVED_MESSAGE = "Connection \"{0}\" recevied request \"{1}\"";

    private static final Map<Long, HttpConnection> threadConnections;
    static {
        threadConnections = new HashMap<>();
    }

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Executor executor;
    private ServerHandler serverHandler;
    private ScheduledExecutorService timer;
    private Logger logger;
    private Properties properties;
    private boolean running;
    private final Set<HttpContext> contexts;
    private final Set<HttpConnection> idleConnections;
    private final Set<HttpConnection> readyConnections;
    private Map<UUID, HttpSession> sessions;

    /**
     * Constructor for the http server
     */
    public HttpServer() {
        running = false;
        properties = new Properties();
        logger = Logger.getAnonymousLogger();
        executor = new Executor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
        serverHandler = new ServerHandler();
        timer = Executors.newSingleThreadScheduledExecutor();
        contexts = Collections.synchronizedSet(new HashSet<HttpContext>());
        idleConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        readyConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        sessions = Collections.synchronizedMap(new HashMap<UUID, HttpSession>());
    }

    /**
     * Get the http server properties
     * @return properties
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * Set the http server properties
     * @param properties
     */
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * Set a property value
     * @param key name of property
     * @param value value of property
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * Get the value of a property
     * @param key name of property
     * @param <R> type of response
     * @return casted response
     */
    public <R> R getProperty (String key) {
        return (R)properties.get(key);
    }

    /**
     * Get the value of a property diven a default value
     * @param key name of property
     * @param defaultValue default value
     * @param <R> type of response
     * @return casted response
     */
    public <R> R getProperty (String key, R defaultValue) {
        R value = (R)properties.get(key);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Retrieves the logger of the server
     * @return Logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Sets the logger of the server
     * @param logger logger
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Log message of the server
     * @param level level
     * @param message message
     * @param arguments arguments
     */
    private void log (Level level, String message, Object ... arguments) {
        if (logger != null && getProperty(LOGGING_ENABLED_PROPERTY_NAME, DEFAULT_LOGGING_ENABLED)) {
            logger.log(level, MessageFormat.format(message, arguments));
        }
    }

    /**
     * Retrieves the thread executor for the http server
     * @return Thread executor
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Sets the thread executor for the http server
     * @param executor Thread executor
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * Adds a new Http Context
     * @param context Context to add
     */
    public void addContext (HttpContext context) {
        contexts.add(context);
    }

    /**
     * Removes an http context
     * @param context Context to remove
     */
    public void removeContext (HttpContext context) {
        contexts.remove(context);
    }

    /**
     * Finds a context for the current request
     * @param request current reques
     * @return http context
     */
    public HttpContext findContext (HttpRequest request) {
        HttpContext matchContext = null;
        for (HttpContext context : contexts) {
            if (request.getPath().startsWith(context.getPath())) {
                matchContext = context;
                break;
            }
        }
        return matchContext;
    }

    /**
     * Gets the current thread active connection
     * @return The connection for the current thread
     */
    public static HttpConnection getCurrentThreadConnection () {
        return threadConnections.get(Thread.currentThread().getId());
    }

    /**
     * Starts the http server
     */
    public void start() {

        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(getProperty(PORT_PROPERTY_NAME, DEFAULT_PORT)));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            int connectionCheckoutInterval = getProperty(CONNECTION_CHECKOUT_INTERVAL_PROPERTY_NAME, DEFAULT_CONNECTION_CHECKOUT_INTERVAL);
            int sessionCheckoutInterval = getProperty(SESSION_CHECKOUT_INTERVAL_PROPERTY_NAME, DEFAULT_SESSION_CHECKOUT_INTERVAL);
            timer.scheduleAtFixedRate(new ConnectionsHandler(),connectionCheckoutInterval,connectionCheckoutInterval,TimeUnit.MILLISECONDS);
            timer.scheduleAtFixedRate(new SessionsHandler(),sessionCheckoutInterval,sessionCheckoutInterval,TimeUnit.MILLISECONDS);

        } catch (Exception ex) {
            throw new HttpException("Error creating server socket", ex);
        }

        Thread dispatcherThread = new Thread(serverHandler);
        running = true;
        dispatcherThread.start();
    }

    /**
     * Stops the http server
     */
    public void stop() {

        running = false;
        try {
            selector.close();
        } catch (Exception ex) {
        }
        try {
            serverChannel.close();
        } catch (Exception ex) {
        }
        timer.shutdownNow();
        selector = null;
        serverChannel = null;
    }

    /**
     * Creates a session for the given connection
     * @param connection connection
     * @return created http session
     */
    protected HttpSession createSession(HttpConnection connection) {
        int maxInactiveInterval = getProperty(SESSION_MAX_INACTIVE_INTERVAL_PROPERTY_NAME, DEFAULT_SESSION_MAX_INACTIVE_INTERVAL);
        HttpSession session = new HttpSession();
        sessions.put(session.getId(), session);
        if (getProperty(SESSION_USE_COOKIES_PROPERTY_NAME, DEFAULT_SESSION_USE_COOKIES)) {
            HttpCookie cookie = new HttpCookie(getProperty(SESSION_NAME_PROPERTY_NAME, DEFAULT_SESSION_NAME), session.getId().toString());
            cookie.setMaxAge(maxInactiveInterval);
            connection.getExchange().addCookie(cookie);
        }
        return session;
    }

    /**
     * Destroys the session for the given connection
     * @param session session to destroy
     * @return destroyed http session
     */
    protected HttpSession destroySession(HttpSession session) {
        session.clearAttributes();
        sessions.remove(session.getId());
        return session;
    }

    /**
     * Get a session from the given connection
     * @param connection connection
     * @return http session
     */
    protected HttpSession getSession(HttpConnection connection) {
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
        String sessionName = getProperty(SESSION_NAME_PROPERTY_NAME, DEFAULT_SESSION_NAME);
        if (getProperty(SESSION_USE_COOKIES_PROPERTY_NAME, DEFAULT_SESSION_USE_COOKIES)) {
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
     * Server handler
     */
    private class ServerHandler implements Runnable {

        @Override
        public void run() {
            while (running) {
                try {
                    //Reconnect ready connections
                    synchronized (readyConnections) {
                        Iterator<HttpConnection> iterator = readyConnections.iterator();
                        while (iterator.hasNext()) {
                            HttpConnection connection = iterator.next();
                            try {
                                SocketChannel clientChannel = connection.getChannel();
                                SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                clientReadKey.attach(connection);
                                iterator.remove();
                                idleConnections.add(connection);
                            }
                            catch (Exception ex) {}
                        }
                    }

                    selector.select(1000);
                    Iterator<SelectionKey> selectorIterator = selector.selectedKeys().iterator();
                    while (selectorIterator.hasNext()) {
                        SelectionKey key = selectorIterator.next();
                        selectorIterator.remove();
                        if (key.isValid()) {
                            try {
                                if (key.isAcceptable()) {
                                    SocketChannel clientChannel = serverChannel.accept();
                                    clientChannel.configureBlocking(false);
                                    SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                    HttpConnection connection = new HttpConnection(HttpServer.this, clientChannel);
                                    clientReadKey.attach(connection);
                                    idleConnections.add(connection);
                                    log(Level.FINE, CONNECTION_CREATED_MESSAGE, connection);
                                }
                                else if (key.isReadable()) {
                                    HttpConnection connection = (HttpConnection) key.attachment();
                                    key.cancel();
                                    idleConnections.remove(connection);
                                    executor.execute(new ClientHandler(connection));
                                }
                            } catch (Exception ex) {}
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Client handler
     */
    private class ClientHandler implements Runnable {

        private final HttpConnection connection;

        public ClientHandler(HttpConnection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {

            boolean closeConnection = true;

            threadConnections.put(Thread.currentThread().getId(), connection);
            try {
                try {
                    //Starts the http exchange
                    HttpExchange exchange = connection.createExchange();
                    log(Level.FINE, CONNECTION_REQUEST_RECEIVED_MESSAGE, connection, exchange.getRequestPath());

                    //Add general response headers
                    exchange.addResponseHeader(HttpHeader.SERVER, getProperty(SERVER_NAME_PROPERTY_NAME, DEFAULT_SERVER_NAME));
                    exchange.addResponseHeader(HttpHeader.DATE, HttpServerUtils.formatDate(new Date()));
                    String connectionHeader = exchange.getRequestHeader(HttpHeader.CONNECTION);
                    if (connectionHeader == null || connectionHeader.equals(HttpHeader.KEEP_ALIVE)) {
                        exchange.addResponseHeader(HttpHeader.CONNECTION, (HttpHeader.KEEP_ALIVE));
                        closeConnection = false;
                    } else {
                        exchange.addResponseHeader(HttpHeader.CONNECTION, (HttpHeader.CLOSE));
                    }

                    //Execute the context that matches the request
                    HttpRequest request = new HttpRequest(connection);
                    HttpContext matchContext = findContext(request);
                    if (matchContext != null) {
                        HttpResponse response = matchContext.onContext(request);
                        response.flush();
                    } else {
                        HttpResponse response = new HttpResponse(connection);
                        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
                        response.addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_PLAIN);
                        response.setBody("No context found for request path \"" + request.getPath() + "\" !!");
                        response.flush();
                    }
                }
                catch (HttpBadRequestException badRequestException) {
                    HttpResponse response = new HttpResponse(connection);
                    response.setResponseCode(HttpResponseCode.HTTP_BAD_REQUEST);
                    response.addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_PLAIN);
                    response.setBody("Bad request !!");
                    response.flush();
                    closeConnection = true;
                }
                catch (HttpException httpException) {
                    HttpResponse response = new HttpResponse(connection);
                    response.setResponseCode(HttpResponseCode.HTTP_INTERNAL_ERROR);
                    response.addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_PLAIN);
                    response.setBody("Connection error !!");
                    response.flush();
                    closeConnection = true;
                }
                catch (Throwable exception) {
                    HttpResponse response = new HttpResponse(connection);
                    response.setResponseCode(HttpResponseCode.HTTP_INTERNAL_ERROR);
                    response.addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_PLAIN);
                    response.setBody("Internal error !!");
                    response.flush();
                }
            }
            catch (Throwable ex) {
                closeConnection = true;
            }
            finally {
                threadConnections.remove(Thread.currentThread().getId());
            }

            //Close a connection
            if (!connection.isClosed()) {
                if (closeConnection) {
                    connection.close();
                } else {
                    readyConnections.add(connection);
                    selector.wakeup();
                }
            }
        }
    }

    /**
     * Handler that manages all connections
     * Removes connections that are inactive
     */
    private class ConnectionsHandler implements Runnable {
        @Override
        public void run() {
            long time = System.currentTimeMillis();
            int maxIdleConnectionInterval = getProperty(CONNECTION_MAX_INACTIVE_INTERVAL_PROPERTY_NAME, DEFAULT_CONNECTION_MAX_INACTIVE_INTERVAL);
            synchronized (idleConnections) {
                Iterator<HttpConnection> iterator = idleConnections.iterator();
                while (iterator.hasNext()) {
                    HttpConnection connection = iterator.next();
                    if ((time - connection.getLastActivityTimestamp()) > maxIdleConnectionInterval) {
                        connection.close();
                        iterator.remove();
                        log(Level.FINE, CONNECTION_DESTROYED_MESSAGE, connection);
                    }
                }
            }
        }
    }

    /**
     * Handler that manages inactive sessions
     * Removes all session that are inactive
     */
    private class SessionsHandler implements Runnable {
        @Override
        public void run() {
            long time = System.currentTimeMillis();
            int maxSessionInactiveInterval = getProperty(SESSION_MAX_INACTIVE_INTERVAL_PROPERTY_NAME, DEFAULT_SESSION_MAX_INACTIVE_INTERVAL);
            synchronized (sessions) {
                Iterator<Map.Entry<UUID, HttpSession>> iterator = sessions.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, HttpSession> entry = iterator.next();
                    HttpSession session = entry.getValue();
                    if ((time - session.getLastActivityTimestamp()) > maxSessionInactiveInterval) {
                        iterator.remove();
                    }
                }
            }
        }
    }
}