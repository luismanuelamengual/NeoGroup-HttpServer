
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

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Executor executor;
    private ServerHandler serverHandler;
    private boolean running;
    private Set<HttpConnection> readyConnections;
    private Set<HttpConnection> idleConnections;

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
            idleConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
            readyConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
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

                    synchronized (readyConnections) {
                        readyConnections.forEach(connection -> {
                            try {
                                SocketChannel clientChannel = connection.getChannel();
                                clientChannel.configureBlocking(false);
                                SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                clientReadKey.attach(connection);
                            }
                            catch (Exception ex) {}
                        });
                        readyConnections.clear();
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
                                    System.out.println("ACCEPT !! " + connection.toString());
                                    clientChannel.configureBlocking(false);
                                    SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                    clientReadKey.attach(connection);
                                } else if (key.isReadable()) {
                                    SocketChannel clientChannel = (SocketChannel)key.channel();
                                    HttpConnection connection = (HttpConnection) key.attachment();
                                    key.cancel();
                                    System.out.println("READ !! " + connection.toString());
                                    executor.execute(new ClientHandler(connection));
                                }
                            } catch (Exception ex) {
                                System.err.println("Error handling client: " + key.channel());
                            }
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
            idleConnections.remove(connection);

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

                response.write("<html><head><link rel=\"stylesheet\" type=\"text/css\" href=\"mystyle.css\"></head><body>Hola mundako</body></html>");
                response.flush();

            } catch (HttpError error) {
                closeConnection = true;
            } catch (Throwable ex) {
                ex.printStackTrace();
                closeConnection = true;
            }

            //Cerrar la conexión
            if (closeConnection) {
                connection.close();
            }
            else {
                readyConnections.add(connection);
                selector.wakeup();
            }
        }
    }
}