package org.neogroup.net.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class HttpRequest {

    private final static int BUF_LEN = 2048;
    private final static byte CR = 13;
    private final static byte LF = 10;

    private final InputStream inputStream;
    private String method;
    private URI uri;
    private String version;
    private HttpHeaders headers;
    private byte[] body;

    public HttpRequest (InputStream inputStream) throws IOException {
        this.inputStream = inputStream;
        readRequestLine();
        readHeaders();
    }

    private void readRequestLine () throws IOException {

        String requestLine = null;
        do {
            char[] buf = new char [BUF_LEN];
            boolean gotCR = false, gotLF = false;
            int pos = 0;
            StringBuffer lineBuf = new StringBuffer();
            while (!gotLF) {
                int c = inputStream.read();
                if (c == -1) {
                    throw new IOException("Socket closed remotely !!");
                }
                if (gotCR) {
                    if (c == LF) {
                        gotLF = true;
                    } else {
                        gotCR = false;
                        if (pos == BUF_LEN) {
                            lineBuf.append (buf);
                            pos = 0;
                        }
                        buf[pos++] = CR;
                        if (pos == BUF_LEN) {
                            lineBuf.append (buf);
                            pos = 0;
                        }
                        buf[pos++] = (char)c;
                    }
                } else {
                    if (c == CR) {
                        gotCR = true;
                    } else {
                        if (pos == BUF_LEN) {
                            lineBuf.append (buf);
                            pos = 0;
                        }
                        buf[pos++] = (char)c;
                    }
                }
            }
            lineBuf.append (buf, 0, pos);
            requestLine = lineBuf.toString();
        } while (requestLine.isEmpty());

        if (requestLine == null) {
            throw new IOException("Bad request line !!");
        }
        int space = requestLine.indexOf (' ');
        if (space == -1) {
            throw new IOException("Bad request line !!");
        }
        method = requestLine.substring (0, space);
        int start = space+1;
        space = requestLine.indexOf(' ', start);
        if (space == -1) {
            throw new IOException("Bad request line !!");
        }
        String uriStr = requestLine.substring (start, space);
        try { uri = new URI (uriStr); } catch (Exception ex) {}
        start = space+1;
        version = requestLine.substring (start);
    }

    private void readHeaders () throws IOException {

        headers = new HttpHeaders();

        char s[] = new char[10];
        int len = 0;
        int firstc = inputStream.read();

        if (firstc == CR || firstc == LF) {
            int c = inputStream.read();
            if (c == CR || c == LF) {
                return;
            }
            s[0] = (char)firstc;
            len = 1;
            firstc = c;
        }

        while (firstc != LF && firstc != CR && firstc >= 0) {
            int keyend = -1;
            int c;
            boolean inKey = firstc > ' ';
            s[len++] = (char) firstc;
            parseloop:{
                while ((c = inputStream.read()) >= 0) {
                    switch (c) {
                        case ':':
                            if (inKey && len > 0)
                                keyend = len;
                            inKey = false;
                            break;
                        case '\t':
                            c = ' ';
                        case ' ':
                            inKey = false;
                            break;
                        case CR:
                        case LF:
                            firstc = inputStream.read();
                            if (c == CR && firstc == LF) {
                                firstc = inputStream.read();
                                if (firstc == CR)
                                    firstc = inputStream.read();
                            }
                            if (firstc == LF || firstc == CR || firstc > ' ')
                                break parseloop;
                            c = ' ';
                            break;
                    }
                    if (len >= s.length) {
                        char ns[] = new char[s.length * 2];
                        System.arraycopy(s, 0, ns, 0, len);
                        s = ns;
                    }
                    s[len++] = (char) c;
                }
                firstc = -1;
            }
            while (len > 0 && s[len - 1] <= ' ')
                len--;
            String k;
            if (keyend <= 0) {
                k = null;
                keyend = 0;
            } else {
                k = String.copyValueOf(s, 0, keyend);
                if (keyend < len && s[keyend] == ':')
                    keyend++;
                while (keyend < len && s[keyend] <= ' ')
                    keyend++;
            }
            String v;
            if (keyend >= len)
                v = new String();
            else
                v = String.copyValueOf(s, keyend, len - keyend);

            headers.put (k,v);
            len = 0;
        }
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public String getVersion() {
        return version;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public String getHeaader (String headerName) {
        return headers.get(headerName);
    }

    public byte[] getBody() {
        return body;
    }
}