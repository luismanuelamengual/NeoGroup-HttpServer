
package org.neogroup.net.sunserver;

import java.io.IOException;
import java.io.OutputStream;

public class PlaceholderOutputStream extends OutputStream {

    OutputStream wrapped;

    PlaceholderOutputStream (OutputStream os) {
        wrapped = os;
    }

    void setWrappedStream (OutputStream os) {
        wrapped = os;
    }

    boolean isWrapped () {
        return wrapped != null;
    }

    private void checkWrap () throws IOException {
        if (wrapped == null) {
            throw new IOException ("response headers not sent yet");
        }
    }

    public void write(int b) throws IOException {
        checkWrap();
        wrapped.write (b);
    }

    public void write(byte b[]) throws IOException {
        checkWrap();
        wrapped.write (b);
    }

    public void write(byte b[], int off, int len) throws IOException {
        checkWrap();
        wrapped.write (b, off, len);
    }

    public void flush() throws IOException {
        checkWrap();
        wrapped.flush();
    }

    public void close() throws IOException {
        checkWrap();
        wrapped.close();
    }
}