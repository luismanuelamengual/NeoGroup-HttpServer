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
    private HttpHeaders headers;
    private URI uri;
    private byte[] body;

    public HttpRequest (InputStream inputStream) {
        this.inputStream = inputStream;
    }

    private void readHeading () throws IOException {
        String startLine = null;
        do {
            startLine = readLine();
            if (startLine == null) {
                return;
            }
        } while (startLine == null ? false : startLine.equals (""));
    }

    private String readLine () throws IOException {

        char[] buf = new char [BUF_LEN];
        boolean gotCR = false, gotLF = false;
        int pos = 0;
        StringBuffer lineBuf = new StringBuffer();
        while (!gotLF) {
            int c = inputStream.read();
            if (c == -1) {
                return null;
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
        return new String (lineBuf);
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

    public void setMethod(String method) {
        this.method = method;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}