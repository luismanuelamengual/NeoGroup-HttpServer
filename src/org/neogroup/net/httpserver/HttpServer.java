
package org.neogroup.net.httpserver;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.Executor;

public class HttpServer {

    public static final String SERVER_NAME = "NeoGroup-HttpServer";

    private static final int DEFAULT_PORT = 80;
    private static final long CONNECTION_CHECKOUT_INTERVAL = 10000;
    private static final long MAX_IDLE_CONNECTION_INTERVAL = 5000;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Executor executor;
    private ServerHandler serverHandler;
    private boolean running;
    private Set<HttpContext> contexts;
    private Set<HttpConnection> idleConnections;
    private Set<HttpConnection> runningConnections;
    private Set<HttpConnection> readyConnections;
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
            contexts = Collections.synchronizedSet(new HashSet<HttpContext>());
            idleConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
            runningConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
            readyConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
            lastConnectionCheckoutTimestamp = System.currentTimeMillis();
        } catch (Exception ex) {
            throw new RuntimeException("Error creating HttpServer", ex);
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
                                System.out.println("RE-REGISTER " + connection.toString());
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
                                    System.out.println ("DELETE " + connection.toString());
                                    iterator.remove();
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
                                    System.out.println("REGISTER " + connection.toString());
                                    clientChannel.configureBlocking(false);
                                    SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                    clientReadKey.attach(connection);
                                    connection.setRegistrationTimestamp(System.currentTimeMillis());
                                    idleConnections.add(connection);
                                }
                                else if (key.isReadable()) {
                                    SocketChannel clientChannel = (SocketChannel)key.channel();
                                    HttpConnection connection = (HttpConnection) key.attachment();
                                    key.cancel();
                                    System.out.println("READ " + connection.toString());
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

            boolean closeConnection = true;

            try {
                //Obtención de la petición y la respuesta de la conexión
                HttpRequest request = connection.getRequest();
                HttpResponse response = connection.getResponse();

                //Iniciar una petición nueva
                request.startNewRequest();

                System.out.println("REQUEST " + connection.toString() + ": " + request.getPath());

                //Iniciar una respuesta nueva
                response.startNewResponse();
                response.addHeader(HttpHeader.DATE, HttpServerUtils.formatDate(new Date()));
                response.addHeader(HttpHeader.SERVER, SERVER_NAME);
                String connectionHeader = request.getHeader(HttpHeader.CONNECTION);
                if (connectionHeader == null || connectionHeader.equals(HttpHeader.KEEP_ALIVE)) {
                    response.addHeader(HttpHeader.CONNECTION, HttpHeader.KEEP_ALIVE);
                    closeConnection = false;
                } else {
                    response.addHeader(HttpHeader.CONNECTION, HttpHeader.CLOSE);
                }

                //Ejecutar el contexto que coincida con el path de la petición
                HttpContext matchContext = null;
                for (HttpContext context : contexts) {
                    if (request.getPath().startsWith(context.getPath())) {
                        matchContext = context;
                        break;
                    }
                };
                if (matchContext != null) {
                    matchContext.onContext(request, response);
                }

                //Escribir datos que hayan quedado almacenados en la respuesta http
                response.flush();
            }
            catch (Throwable ex) {
                closeConnection = true;
            }

            runningConnections.remove(connection);

            //Cerrar la conexión
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