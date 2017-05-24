
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
    public static final String MAX_IDLE_CONNECTION_INTERVAL_PROPERTY_NAME = "maxIdleConnectionInterval";

    public static final String SERVER_NAME_DEFAULT_VALUE = "NeoGroup-HttpServer";

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
    private HttpSessionManager sessionManager;
    private Logger logger;
    private Properties properties;
    private boolean running;
    private final Set<HttpContext> contexts;
    private final Set<HttpConnection> idleConnections;
    private final Set<HttpConnection> readyConnections;
    private long lastConnectionCheckoutTimestamp;

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
        contexts = Collections.synchronizedSet(new HashSet<HttpContext>());
        idleConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        readyConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        lastConnectionCheckoutTimestamp = System.currentTimeMillis();
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
        if (logger != null && getProperty(LOGGING_ENABLED_PROPERTY_NAME, false)) {
            logger.log(level, MessageFormat.format(message, arguments));
        }
    }

    /**
     * Get the session manager
     * @return session manager
     */
    public HttpSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Set the session manager
     * @param sessionManager session manager
     */
    public void setSessionManager(HttpSessionManager sessionManager) {
        this.sessionManager = sessionManager;
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
            serverChannel.socket().bind(new InetSocketAddress(getProperty(PORT_PROPERTY_NAME, 80)));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
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
        selector = null;
        serverChannel = null;
    }

    /**
     * Server handler
     */
    private class ServerHandler implements Runnable {

        @Override
        public void run() {

            long connectionCheckoutInterval = getProperty(CONNECTION_CHECKOUT_INTERVAL_PROPERTY_NAME, 10000);
            long maxIdleConnectionInterval = getProperty(MAX_IDLE_CONNECTION_INTERVAL_PROPERTY_NAME, 5000);
            while (running) {
                try {

                    long time = System.currentTimeMillis();

                    //Reconnect ready connections
                    synchronized (readyConnections) {
                        Iterator<HttpConnection> iterator = readyConnections.iterator();
                        while (iterator.hasNext()) {
                            HttpConnection connection = iterator.next();
                            try {
                                SocketChannel clientChannel = connection.getChannel();
                                clientChannel.configureBlocking(false);
                                SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                clientReadKey.attach(connection);
                                iterator.remove();
                                idleConnections.add(connection);
                            }
                            catch (Exception ex) {}
                        }
                    }

                    //Remove connections without activity
                    if ((time - lastConnectionCheckoutTimestamp) > connectionCheckoutInterval) {
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
                        lastConnectionCheckoutTimestamp = time;
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
                                    HttpConnection connection = new HttpConnection(HttpServer.this, clientChannel);
                                    clientChannel.configureBlocking(false);
                                    SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                    clientReadKey.attach(connection);
                                    idleConnections.add(connection);
                                    log(Level.FINE, CONNECTION_CREATED_MESSAGE, connection);
                                }
                                else if (key.isReadable()) {
                                    SocketChannel clientChannel = (SocketChannel)key.channel();
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
                    exchange.addResponseHeader(HttpHeader.SERVER, getProperty(SERVER_NAME_PROPERTY_NAME, SERVER_NAME_DEFAULT_VALUE));
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
}