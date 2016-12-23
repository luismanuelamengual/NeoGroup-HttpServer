
package org.neogroup.net.httpserver;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;

public class HttpConnection {

    private final SocketChannel channel;
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean closed;

    public HttpConnection(SocketChannel channel) {
        this.channel = channel;
        this.inputStream = new HttpInputStream(channel);
        this.outputStream = new HttpOutputStream(channel);
        closed = false;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public synchronized void close() {
        if (!closed) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ex) {
                }
                inputStream = null;
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ex) {
                }
                outputStream = null;
            }
            try {
                channel.close();
            } catch (Exception ex) {
            }
            closed = true;
        }
    }
}
