
package org.neogroup.net.httpserver;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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

    public HttpServer () {
        this(DEFAULT_PORT);
    }

    public HttpServer (int port) {

        try {
            running = false;
            executor = new Executor() { @Override public void execute(Runnable task) { task.run(); } };
            serverHandler = new ServerHandler();
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        }
        catch (Exception ex) {
            throw new RuntimeException("Error creating HttpServer", ex);
        }
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void start () {

        Thread dispatcherThread = new Thread (serverHandler);
        running = true;
        dispatcherThread.start();
    }

    public void stop () {

        running = false;
        try { selector.close(); } catch (Exception ex) {}
        try { serverChannel.close(); } catch (Exception ex) {}
    }

    private class ServerHandler implements Runnable {

        @Override
        public void run() {

            while (running) {
                try {
                    selector.select(1000);
                    Iterator<SelectionKey> selectorIterator = selector.selectedKeys().iterator();
                    while (selectorIterator.hasNext()) {
                        SelectionKey key = selectorIterator.next();
                        selectorIterator.remove();
                        if (key.isValid()) {
                            try {
                                if (key.isAcceptable()) {
                                    SocketChannel clientChannel = serverChannel.accept();
                                    clientChannel.configureBlocking (false);
                                    clientChannel.register(selector, SelectionKey.OP_READ);
                                } else if (key.isReadable()) {
                                    SocketChannel channel = (SocketChannel) key.channel();
                                    key.cancel();
                                    executor.execute(new ClientHandler(channel));
                                }
                            } catch (Exception ex) {
                                System.err.println("Error handling client: " + key.channel());
                            }
                        }
                    }
                }
                catch (Exception ex) {}
            }
        }
    }

    private class ClientHandler implements Runnable {

        private final SocketChannel channel;

        public ClientHandler(SocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {

            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                inputStream = new HttpInputStream(channel);
                outputStream = new HttpOutputStream(channel);

                HttpRequest request = new HttpRequest(inputStream);
                HttpResponse response = new HttpResponse(outputStream);

                System.out.println ("=================");
                System.out.println (request.getMethod());
                System.out.println (request.getUri());
                System.out.println (request.getVersion());
                System.out.println (request.getHeader("User-Agent"));
                System.out.println (request.getParameter("name"));


                response.write("hola mundelich");

                response.send();
            }
            catch (Throwable ex) {
                ex.printStackTrace();
            }

            //Cerrar la conexión con el canal
            try { inputStream.close(); } catch (Exception ex) {}
            try { outputStream.close(); } catch (Exception ex) {}
            try { channel.close(); } catch (Exception ex) {}
        }
    }
}