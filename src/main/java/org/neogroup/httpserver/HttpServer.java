
package org.neogroup.httpserver;

import org.neogroup.httpserver.contexts.HttpContext;
import org.neogroup.httpserver.utils.MimeTypes;

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

public class HttpServer {

    public static final String CONNECTION_CREATED_MESSAGE = "Connection \"{0}\" created !!";
    public static final String CONNECTION_DESTROYED_MESSAGE = "Connection \"{0}\" destroyed !!";
    public static final String CONNECTION_REQUEST_RECEIVED_MESSAGE = "Connection \"{0}\" recevied request \"{1}\"";

    public static final String SERVER_NAME = "NeoGroup-HttpServer";

    private static final int DEFAULT_PORT = 80;
    private static final long CONNECTION_CHECKOUT_INTERVAL = 10000;
    private static final long MAX_IDLE_CONNECTION_INTERVAL = 5000;

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

    public HttpServer() {
        this(DEFAULT_PORT);
    }

    public HttpServer(int port) {

        try {
            running = false;
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
            logger = Logger.getLogger(SERVER_NAME);
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

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void addContext (HttpContext context) {
        contexts.add(context);
    }

    public void removeContext (HttpContext context) {
        contexts.remove(context);
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    public void start() {

        Thread dispatcherThread = new Thread(serverHandler);
        running = true;
        dispatcherThread.start();
    }

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

    private void log (Level level, String message, Object ... arguments) {
        if (loggingEnabled && logger != null) {
            logger.log(level, MessageFormat.format(message, arguments));
        }
    }

    private class ServerHandler implements Runnable {

        @Override
        public void run() {

            while (running) {
                try {

                    long time = System.currentTimeMillis();

                    //Reconectar conexiones
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

                    //Eliminar conexiones sin actividad
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
                                    HttpConnection connection = new HttpConnection(clientChannel);
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

    private class ClientHandler implements Runnable {

        private final HttpConnection connection;

        public ClientHandler(HttpConnection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {

            connection.setAutoClose(true);

            //Obtención de la petición y la respuesta de la conexión
            HttpConnection.setActiveConnection(connection);
            try {
                try {
                    //Iniciar una petición nueva
                    HttpRequest request = new HttpRequest(connection);
                    log(Level.FINE, CONNECTION_REQUEST_RECEIVED_MESSAGE, connection, request.getPath());

                    String connectionHeader = request.getHeader(HttpHeader.CONNECTION);
                    if (connectionHeader == null || connectionHeader.equals(HttpHeader.KEEP_ALIVE)) {
                        connection.setAutoClose(false);
                    }

                    //Ejecutar el contexto que coincida con el path de la petición
                    HttpContext matchContext = null;
                    for (HttpContext context : contexts) {
                        if (request.getPath().startsWith(context.getPath())) {
                            matchContext = context;
                            break;
                        }
                    }

                    if (matchContext != null) {
                        try {
                            HttpResponse response = matchContext.onContext(request);
                            response.flush();
                        }
                        catch (Throwable contextException) {
                            HttpResponse response = new HttpResponse(connection);
                            response.setResponseCode(HttpResponseCode.HTTP_INTERNAL_ERROR);
                            response.addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_PLAIN);
                            response.setBody("Error processing context request path " + request.getPath() + "\". Error: " + contextException.toString());
                            response.flush();
                        }
                    } else {
                        HttpResponse response = new HttpResponse(connection);
                        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
                        response.addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_PLAIN);
                        response.setBody("No context found for request path \"" + request.getPath() + "\" !!");
                        response.flush();
                    }
                }
                catch (HttpBadRequestException badRequestException) {
                    HttpResponse response = new HttpResponse(connection);
                    response.addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_PLAIN);
                    response.setBody("Bad request !!");
                    response.flush();
                }
            }
            catch (Throwable ex) {
                connection.setAutoClose(true);
            }
            finally {
                HttpConnection.setActiveConnection(null);
            }

            runningConnections.remove(connection);

            //Cerrar la conexión
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