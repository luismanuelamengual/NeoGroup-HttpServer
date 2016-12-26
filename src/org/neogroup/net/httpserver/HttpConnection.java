
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
        this.inputStream = null;
        this.outputStream = null;
        closed = false;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public InputStream getInputStream() {
        if (inputStream == null) {
            try {
                inputStream = channel.socket().getInputStream();
            }
            catch (Exception ex) {
                throw new HttpError("Error retrieving inputStream fron channel");
            }
        }
        return inputStream;
    }

    public OutputStream getOutputStream() {
        if (outputStream == null) {
            try {
                outputStream = channel.socket().getOutputStream();
            }
            catch (Exception ex) {
                throw new HttpError("Error retrieving outputStream fron channel");
            }
        }
        return outputStream;
    }

    public synchronized void close() {
        if (!closed) {

            try {
                channel.shutdownInput();
            } catch (Exception ex) {}
            inputStream = null;

            try {
                channel.shutdownOutput();
            } catch (Exception ex) {}
            outputStream = null;

            try {
                channel.close();
            } catch (Exception ex) {
            }
            closed = true;
        }
    }
}
