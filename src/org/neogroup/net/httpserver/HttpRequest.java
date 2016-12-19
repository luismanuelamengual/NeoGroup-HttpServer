package org.neogroup.net.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

public class HttpRequest {

    private static final String QUERY_PARAMETERS_REGEX = "[&]";
    private static final String QUERY_PARAMETER_VALUES_REGEX = "[=]";
    private static final String FILE_ENCODING_SYSTEM_PROPERTY_NAME = "file.encoding";
    private static final String URI_SEPARATOR = "/";
    private final static int BUF_LEN = 2048;
    private final static byte CR = 13;
    private final static byte LF = 10;

    private final InputStream inputStream;
    private String method;
    private URI uri;
    private String version;
    private Map<String, String> headers;
    private Map<String,String> parameters;
    private byte[] body;

    public HttpRequest (InputStream inputStream) {
        this.inputStream = inputStream;
        try {
            readRequestLine();
            readHeaders();
        }
        catch (Exception ex) {
            throw new HttpError ("Error reading request headers !!", ex);
        }
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public String getQuery() {
        return uri.getRawQuery();
    }

    public String getPath() {
        return uri.getRawPath();
    }

    public List<String> getPathParts() {
        String path = getPath();
        String[] pathTokens = path.split(URI_SEPARATOR);
        return Arrays.asList(pathTokens);
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public String getHeader (String headerName) {
        return headers.get(headerName);
    }

    public byte[] getBody() {
        if (body == null) {
            try {
                readBody();
            }
            catch (Exception ex) {
                throw new HttpError ("Error reading request body !!", ex);
            }
        }
        return body;
    }

    public Map<String,String> getParameters() {

        if (parameters == null) {
            parameters = new HashMap<>();
            readParametersFromQuery(getQuery());
            String requestContentType = getHeader(HttpHeader.CONTENT_TYPE);
            if (requestContentType != null && requestContentType.equals(HttpHeader.APPLICATION_FORM_URL_ENCODED)) {
                readParametersFromQuery(new String(getBody()));
            }
        }
        return parameters;
    }

    public String getParameter (String name) {
        return getParameters().get(name);
    }

    public boolean hasParameter (String name) {
        return getParameters().containsKey(name);
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

        headers = new HashMap<>();

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

    private void readBody () throws IOException {

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            int read = inputStream.read();
            while (read != -1) {
                byteArrayOutputStream.write(read);
                read = inputStream.read();
            }
            body = byteArrayOutputStream.toByteArray();
        }
    }

    private void readParametersFromQuery(String query) {

        try {
            if (query != null) {
                String pairs[] = query.split(QUERY_PARAMETERS_REGEX);
                for (String pair : pairs) {
                    String param[] = pair.split(QUERY_PARAMETER_VALUES_REGEX);
                    String key = null;
                    String value = null;
                    if (param.length > 0) {
                        key = URLDecoder.decode(param[0], System.getProperty(FILE_ENCODING_SYSTEM_PROPERTY_NAME));
                    }
                    if (param.length > 1) {
                        value = URLDecoder.decode(param[1], System.getProperty(FILE_ENCODING_SYSTEM_PROPERTY_NAME));
                    }
                    parameters.put(key, value);
                }
            }
        }
        catch (Exception ex) {
            throw new HttpError("Error reading request parameters !!", ex);
        }
    }
}