
package org.neogroup.net.httpserver;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Executor;

public class HttpServer {

    private static final int DEFAULT_PORT = 80;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Executor executor;
    private Dispatcher dispatcher;
    private boolean running;

    public HttpServer () {
        this(DEFAULT_PORT);
    }

    public HttpServer (int port) {

        try {
            running = false;
            executor = new Executor() { @Override public void execute(Runnable task) { task.run(); } };
            dispatcher = new Dispatcher();
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

    private class Dispatcher implements Runnable {

        @Override
        public void run() {

            while (running) {
                try {
                    selector.select(1000);
                    Iterator<SelectionKey> selectorIterator = selector.selectedKeys().iterator();
                    while (selectorIterator.hasNext()) {
                        SelectionKey key = selectorIterator.next();
                        selectorIterator.remove();
                        if (!key.isValid()) {
                            continue;
                        }

                        try {
                            if (key.isAcceptable()) {
                                SocketChannel clientChannel = serverChannel.accept();
                                clientChannel.configureBlocking(false);
                                SelectionKey readKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                HttpConnection connection = new HttpConnection(clientChannel);
                                readKey.attach(connection);
                            }
                            else if (key.isReadable()) {
                                HttpConnection connection = (HttpConnection) key.attachment();
                                executor.execute(new Exchange(connection));
                            }
                        }
                        catch (Exception ex) {
                            System.err.println("Error handling client: " + key.channel());
                        }
                    }
                }
                catch (Exception ex) {}
            }
        }
    }

    private class Exchange implements Runnable {

        private HttpConnection connection;

        public Exchange(HttpConnection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {

        }
    }
}



/*
package net.shadowraze.vote4diamondz;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class WebServer implements Runnable {

    private Charset charset = Charset.forName("UTF-8");
    private CharsetEncoder encoder = charset.newEncoder();
    private Selector selector = Selector.open();
    private ServerSocketChannel server = ServerSocketChannel.open();
    private boolean isRunning = true;
    private boolean debug = true;

    protected WebServer(InetSocketAddress address) throws IOException {
        server.socket().bind(address);
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    public final void run() {
        if (isRunning) {
            try {
                selector.selectNow();
                Iterator<SelectionKey> i = selector.selectedKeys().iterator();
                while (i.hasNext()) {
                    SelectionKey key = i.next();
                    i.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    try {
                        // get a new connection
                        if (key.isAcceptable()) {
                            // accept them
                            SocketChannel client = server.accept();
                            // non blocking please
                            client.configureBlocking(false);
                            // show out intentions
                            client.register(selector, SelectionKey.OP_READ);
                            // read from the connection
                        } else if (key.isReadable()) {
                            //  get the client
                            SocketChannel client = (SocketChannel) key.channel();
                            // get the session
                            HTTPSession session = (HTTPSession) key.attachment();
                            // create it if it doesnt exist
                            if (session == null) {
                                session = new HTTPSession(client);
                                key.attach(session);
                            }
                            // get more data
                            session.readData();
                            // decode the message
                            String line;
                            while ((line = session.readLine()) != null) {
                                // check if we have got everything
                                if (line.isEmpty()) {
                                    HTTPRequest request = new HTTPRequest(session.readLines.toString());
                                    session.sendResponse(handle(session, request));
                                    session.close();
                                }
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Error handling client: " + key.channel());
                        if (debug) {
                            ex.printStackTrace();
                        } else {
                            System.err.println(ex);
                            System.err.println("\tat " + ex.getStackTrace()[0]);
                        }
                        if (key.attachment() instanceof HTTPSession) {
                            ((HTTPSession) key.attachment()).close();
                        }
                    }
                }
            } catch (IOException ex) {
                // call it quits
                shutdown();
                // throw it as a runtime exception so that Bukkit can handle it
                throw new RuntimeException(ex);
            }
        }
    }

    protected HTTPResponse handle(HTTPSession session, HTTPRequest request) throws IOException {
        HTTPResponse response = new HTTPResponse();
        response.setContent("I liek cates".getBytes());
        return response;
    }

    public final void shutdown() {
        isRunning = false;
        try {
            selector.close();
            server.close();
        } catch (IOException ex) {
            // do nothing, its game over
        }
    }

    public final class HTTPSession {

        private final SocketChannel channel;
        private final ByteBuffer buffer = ByteBuffer.allocate(2048);
        private final StringBuilder readLines = new StringBuilder();
        private int mark = 0;

        public HTTPSession(SocketChannel channel) {
            this.channel = channel;
        }

        public String readLine() throws IOException {
            StringBuilder sb = new StringBuilder();
            int l = -1;
            while (buffer.hasRemaining()) {
                char c = (char) buffer.get();
                sb.append(c);
                if (c == '\n' && l == '\r') {
                    // mark our position
                    mark = buffer.position();
                    // append to the total
                    readLines.append(sb);
                    // return with no line separators
                    return sb.substring(0, sb.length() - 2);
                }
                l = c;
            }
            return null;
        }

        public void readData() throws IOException {
            buffer.limit(buffer.capacity());
            int read = channel.read(buffer);
            if (read == -1) {
                throw new IOException("End of stream");
            }
            buffer.flip();
            buffer.position(mark);
        }

        private void writeLine(String line) throws IOException {
            channel.write(encoder.encode(CharBuffer.wrap(line + "\r\n")));
        }

        public void sendResponse(HTTPResponse response) {
            response.addDefaultHeaders();
            try {
                writeLine(response.version + " " + response.responseCode + " " + response.responseReason);
                for (Map.Entry<String, String> header : response.headers.entrySet()) {
                    writeLine(header.getKey() + ": " + header.getValue());
                }
                writeLine("");
                channel.write(ByteBuffer.wrap(response.content));
            } catch (IOException ex) {
                // slow silently
            }
        }

        public void close() {
            try {
                channel.close();
            } catch (IOException ex) {
            }
        }
    }

    public static class HTTPRequest {

        private final String raw;
        private String method;
        private String location;
        private String version;
        private Map<String, String> headers = new HashMap<String, String>();

        public HTTPRequest(String raw) {
            this.raw = raw;
            parse();
        }

        private void parse() {
            // parse the first line
            StringTokenizer tokenizer = new StringTokenizer(raw);
            method = tokenizer.nextToken().toUpperCase();
            location = tokenizer.nextToken();
            version = tokenizer.nextToken();
            // parse the headers
            String[] lines = raw.split("\r\n");
            for (int i = 1; i < lines.length; i++) {
                String[] keyVal = lines[i].split(":", 2);
                headers.put(keyVal[0], keyVal[1]);
            }
        }

        public String getMethod() {
            return method;
        }

        public String getLocation() {
            return location;
        }

        public String getHead(String key) {
            return headers.get(key);
        }
    }

    public static class HTTPResponse {

        private String version = "HTTP/1.1";
        private int responseCode = 200;
        private String responseReason = "OK";
        private Map<String, String> headers = new LinkedHashMap<String, String>();
        private byte[] content;

        private void addDefaultHeaders() {
            headers.put("Date", new Date().toString());
            headers.put("Server", "Java NIO Webserver by md_5");
            headers.put("Connection", "close");
            headers.put("Content-Length", Integer.toString(content.length));
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getResponseReason() {
            return responseReason;
        }

        public String getHeader(String header) {
            return headers.get(header);
        }

        public byte[] getContent() {
            return content;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public void setResponseReason(String responseReason) {
            this.responseReason = responseReason;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }

        public void setHeader(String key, String value) {
            headers.put(key, value);
        }
    }

    public static void main(String[] args) throws Exception {
        WebServer server = new WebServer(new InetSocketAddress(5555));
        while (true) {
            server.run();
            Thread.sleep(100);
        }
    }
}
[/i]
 */