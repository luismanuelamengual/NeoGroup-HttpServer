
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
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Http Server
 */
public class HttpServer {

    private static final String CONNECTION_CREATED_MESSAGE = "Connection \"{0}\" created !!";
    private static final String CONNECTION_DESTROYED_MESSAGE = "Connection \"{0}\" destroyed !!";
    private static final String CONNECTION_REQUEST_RECEIVED_MESSAGE = "Connection \"{0}\" recevied request \"{1}\"";

    private static final int DEFAULT_PORT = 80;
    private static final long CONNECTION_CHECKOUT_INTERVAL = 10000;
    private static final long MAX_IDLE_CONNECTION_INTERVAL = 5000;

    private static final Map<Long, HttpConnection> threadConnections;
    static {
        threadConnections = new HashMap<>();
    }

    private String name;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Executor executor;
    private ServerHandler serverHandler;
    private Logger logger;
    private boolean loggingEnabled;
    private boolean running;
    private final Set<HttpContext> contexts;
    private final Set<HttpConnection> idleConnections;
    private final Set<HttpConnection> runningConnections;
    private final Set<HttpConnection> readyConnections;
    private long lastConnectionCheckoutTimestamp;

    /**
     * Default constructor for the http server. By default the server
     * listens at port 80
     */
    public HttpServer() {
        this(DEFAULT_PORT);
    }

    /**
     * Constructor for the http server listening at a given port
     * @param port Port for the http server to listen
     */
    public HttpServer(int port) {

        try {
            running = false;
            name = "NeoGroup-HttpServer";
            executor = new Executor() {
                @Override
                public void execute(Runnable task) {
                    task.run();
                }
            };
            serverHandler = new ServerHandler();
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            logger = Logger.getAnonymousLogger();
            logger.setLevel(Level.ALL);
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            logger.addHandler(consoleHandler);
            loggingEnabled = false;
            contexts = Collections.synchronizedSet(new HashSet<HttpContext>());
            idleConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
            runningConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
            readyConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
            lastConnectionCheckoutTimestamp = System.currentTimeMillis();
        } catch (Exception ex) {
            throw new HttpException("Error creating HttpServer", ex);
        }
    }

    /**
     * Get the name of the server
     * @return string
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of the server
     * @param name name of server
     */
    public void setName(String name) {
        this.name = name;
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
     * Indicates where logging is enabled or not
     * @return boolean
     */
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * Sets where logging is enabled or not
     * @param loggingEnabled logging enabled
     */
    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
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
    }

    /**
     * Log message of the server
     * @param level level
     * @param message message
     * @param arguments arguments
     */
    private void log (Level level, String message, Object ... arguments) {
        if (loggingEnabled && logger != null) {
            logger.log(level, MessageFormat.format(message, arguments));
        }
    }

    /**
     * Server handler
     */
    private class ServerHandler implements Runnable {

        @Override
        public void run() {

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
                                connection.setRegistrationTimestamp(System.currentTimeMillis());
                                iterator.remove();
                                idleConnections.add(connection);
                            }
                            catch (Exception ex) {}
                        }
                    }

                    //Remove connections without activity
                    if ((time - lastConnectionCheckoutTimestamp) > CONNECTION_CHECKOUT_INTERVAL) {
                        synchronized (idleConnections) {
                            Iterator<HttpConnection> iterator = idleConnections.iterator();
                            while (iterator.hasNext()) {
                                HttpConnection connection = iterator.next();
                                if ((time - connection.getRegistrationTimestamp()) > MAX_IDLE_CONNECTION_INTERVAL) {
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
                                    connection.setRegistrationTimestamp(System.currentTimeMillis());
                                    idleConnections.add(connection);
                                    log(Level.FINE, CONNECTION_CREATED_MESSAGE, connection);
                                }
                                else if (key.isReadable()) {
                                    SocketChannel clientChannel = (SocketChannel)key.channel();
                                    HttpConnection connection = (HttpConnection) key.attachment();
                                    key.cancel();
                                    idleConnections.remove(connection);
                                    runningConnections.add(connection);
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

            connection.setAutoClose(true);

            //Starts a new connection
            threadConnections.put(Thread.currentThread().getId(), connection);

            try {
                //Starts a new request
                HttpRequest request = new HttpRequest(connection);
                log(Level.FINE, CONNECTION_REQUEST_RECEIVED_MESSAGE, connection, request.getPath());

                String connectionHeader = request.getHeader(HttpHeader.CONNECTION);
                if (connectionHeader == null || connectionHeader.equals(HttpHeader.KEEP_ALIVE)) {
                    connection.setAutoClose(false);
                }

                //Execute the context that matches the request
                HttpContext matchContext = findContext(request);
                if (matchContext != null) {
                    try {
                        HttpResponse response = matchContext.onContext(request);
                        response.flush();
                    }
                    catch (Throwable contextException) {
                        HttpResponse response = new HttpResponse(connection);
                        response.setResponseCode(HttpResponseCode.HTTP_INTERNAL_ERROR);
                        response.addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_PLAIN);
                        response.setBody("Error processing context request path " + request.getPath() + "\". Error: " + contextException.toString());
                        response.flush();
                    }
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
                response.addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_PLAIN);
                response.setBody("Bad request !!");
                response.flush();
            }
            catch (Throwable ex) {
                connection.setAutoClose(true);
            }
            finally {
                threadConnections.remove(Thread.currentThread().getId());
            }

            runningConnections.remove(connection);

            //Close a connection
            if (!connection.isClosed()) {
                if (connection.isAutoClose()) {
                    connection.close();
                } else {
                    readyConnections.add(connection);
                    selector.wakeup();
                }
            }
        }
    }
}