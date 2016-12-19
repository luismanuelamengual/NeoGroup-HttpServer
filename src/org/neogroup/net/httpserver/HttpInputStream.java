
package org.neogroup.net.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class HttpInputStream extends InputStream {

    private final static int BUFFER_SIZE = 8 * 1024;

    private SocketChannel channel;
    private ByteBuffer channelBuffer;
    private ByteBuffer markBuf;
    private byte[] one;
    private boolean marked;
    private boolean reset;
    private boolean closed;
    private boolean eof;

    public HttpInputStream (SocketChannel channel) {
        this.channel = channel;
        channelBuffer = ByteBuffer.allocate (BUFFER_SIZE);
        channelBuffer.clear();
        one = new byte[1];
        closed = false;
        marked = false;
        reset = false;
        eof = false;
    }

    public synchronized int read (byte[] b) throws IOException {
        return read (b, 0, b.length);
    }

    public synchronized int read () throws IOException {
        int result = read (one, 0, 1);
        if (result == 1) {
            return one[0] & 0xFF;
        } else {
            return -1;
        }
    }

    public synchronized int read (byte[] b, int off, int srclen) throws IOException {

        int canreturn, willreturn;
        if (closed)
            throw new IOException ("Stream closed");

        if (eof) {
            return -1;
        }

        assert channel.isBlocking();

        if (off < 0 || srclen < 0|| srclen > (b.length-off)) {
            throw new IndexOutOfBoundsException ();
        }

        if (reset) {
            canreturn = markBuf.remaining ();
            willreturn = canreturn>srclen ? srclen : canreturn;
            markBuf.get(b, off, willreturn);
            if (canreturn == willreturn) {
                reset = false;
            }
        }
        else {
            channelBuffer.clear ();
            if (srclen < BUFFER_SIZE) {
                channelBuffer.limit (srclen);
            }
            do {
                willreturn = channel.read (channelBuffer);
            }
            while (willreturn == 0);
            if (willreturn == -1) {
                eof = true;
                return -1;
            }
            channelBuffer.flip ();
            channelBuffer.get(b, off, willreturn);
            if (marked) {
                try {
                    markBuf.put (b, off, willreturn);
                } catch (BufferOverflowException e) {
                    marked = false;
                }
            }
        }
        return willreturn;
    }

    public boolean markSupported () {
        return true;
    }

    public synchronized int available () throws IOException {
        if (closed)
            throw new IOException ("Stream is closed");
        if (eof)
            return -1;
        if (reset)
            return markBuf.remaining();
        return channelBuffer.remaining();
    }

    public void close () throws IOException {
        if (closed) {
            return;
        }
        channel.close ();
        closed = true;
    }

    public synchronized void mark (int readlimit) {
        if (closed)
            return;
        markBuf = ByteBuffer.allocate (readlimit);
        marked = true;
        reset = false;
    }

    public synchronized void reset () throws IOException {
        if (closed)
            return;
        if (!marked)
            throw new IOException ("Stream not marked");
        marked = false;
        reset = true;
        markBuf.flip ();
    }
}