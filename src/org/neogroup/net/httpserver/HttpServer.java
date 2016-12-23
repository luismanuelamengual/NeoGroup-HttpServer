
package org.neogroup.net.httpserver;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.Executor;

public class HttpServer {

    public static final String SERVER_NAME = "NeoGroup-HttpServer";

    private static final int DEFAULT_PORT = 80;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Executor executor;
    private ServerHandler serverHandler;
    private boolean running;
    private Object registeringSync = new Object();

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

                    synchronized (registeringSync) {

                    }

                    selector.select(1000);
                    Iterator<SelectionKey> selectorIterator = selector.selectedKeys().iterator();
                    while (selectorIterator.hasNext()) {
                        SelectionKey key = selectorIterator.next();
                        selectorIterator.remove();
                        if (key.isValid()) {
                            try {
                                if (key.isAcceptable()) {
                                    System.out.println("ACCEPT !!");
                                    SocketChannel clientChannel = serverChannel.accept();
                                    clientChannel.configureBlocking(false);
                                    SelectionKey clientReadKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                    clientReadKey.attach(new HttpConnection(clientChannel));
                                } else if (key.isReadable()) {
                                    System.out.println("READ !!");
                                    HttpConnection connection = (HttpConnection) key.attachment();
                                    key.cancel();
                                    executor.execute(new ClientHandler(connection));
                                }
                            } catch (Exception ex) {
                                System.err.println("Error handling client: " + key.channel());
                            }
                        }
                    }
                } catch (Exception ex) {
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

            System.out.println ("INIT THREAD !!");

            boolean closeConnection = true;

            try {
                //Creación de la peticion HTTP
                HttpRequest request = new HttpRequest(connection.getInputStream());
                request.readRequest();

                //Creación de la respuesta HTTP
                HttpResponse response = new HttpResponse(connection.getOutputStream());
                response.addHeader(HttpHeader.DATE, HttpServerUtils.formatDate(new Date()));
                response.addHeader(HttpHeader.SERVER, SERVER_NAME);
                String connectionHeader = request.getHeader(HttpHeader.CONNECTION);
                if (connectionHeader == null || connectionHeader.equals(HttpHeader.CLOSE)) {
                    response.addHeader(HttpHeader.CONNECTION, HttpHeader.CLOSE);
                } else {
                    response.addHeader(HttpHeader.CONNECTION, HttpHeader.KEEP_ALIVE);
                    closeConnection = false;
                }

                response.write("<html><head><link rel=\"stylesheet\" type=\"text/css\" href=\"mystyle.css\"></head></html>");
                response.sendResponse();

            } catch (Throwable ex) {
                ex.printStackTrace();
                closeConnection = true;
            }

            //Cerrar la conexión
            if (closeConnection) {
                connection.close();
            }
            else {
                synchronized (registeringSync) {
                    selector.wakeup();
                    try {
                        SelectionKey key = connection.getChannel().register(selector, SelectionKey.OP_READ);
                        key.attach(connection);
                    } catch (Throwable ex) {}
                }
            }

            System.out.println ("END THREAD !!");
        }
    }
}