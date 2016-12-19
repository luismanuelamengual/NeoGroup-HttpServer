
package org.neogroup.net.httpserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class HttpOutputStream extends OutputStream {

    private final static int BUFFER_SIZE = 4 * 1024;

    private SocketChannel channel;
    private ByteBuffer channelBuffer;
    private byte[] one;
    private boolean closed;

    public HttpOutputStream (SocketChannel channel) {

        this.channel = channel;
        channelBuffer = ByteBuffer.allocate (BUFFER_SIZE);
        one = new byte [1];
        closed = false;
    }

    public synchronized void write (int b) throws IOException {
        one[0] = (byte)b;
        write (one, 0, 1);
    }

    public synchronized void write (byte[] b) throws IOException {
        write (b, 0, b.length);
    }

    public synchronized void write (byte[] b, int off, int len) throws IOException {
        int l = len;
        if (closed)
            throw new IOException ("stream is closed");

        int cap = channelBuffer.capacity();
        if (cap < len) {
            int diff = len - cap;
            channelBuffer = ByteBuffer.allocate (2*(cap+diff));
        }
        channelBuffer.clear();
        channelBuffer.put (b, off, len);
        channelBuffer.flip ();
        int n;
        while ((n = channel.write (channelBuffer)) < l) {
            l -= n;
            if (l == 0)
                return;
        }
    }

    public void close () throws IOException {
        if (!closed) {
            channel.close();
            closed = true;
        }
    }
}