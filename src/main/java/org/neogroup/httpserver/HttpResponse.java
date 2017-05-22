
package org.neogroup.httpserver;

import org.neogroup.util.MimeUtils;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.*;

/**
 * This class holds information for a Http Response
 */
public class HttpResponse {

    private final static String STATUS_LINE_TEMPLATE = "HTTP/1.1 {0} {1}\r\n";
    private final static String HEADER_LINE_TEMPLATE = "{0}: {1}\r\n";
    private final static String SEPARATOR = "\r\n";
    private final static int WRITE_BUFFER_SIZE = 8 * 1024;
    private final static int HEADERS_WRITE_BUFFER_SIZE = 2048;

    private final HttpConnection connection;
    private int responseCode;
    private Map<String, List<String>> headers;
    private ByteBuffer bodyBuffer;
    private int bodySize;
    private boolean headersSent;

    /**
     * Default constructor for a http response
     */
    public HttpResponse () {
        this(HttpServer.getCurrentThreadConnection());
    }

    /**
     * Constructor for a response with thte associated connection
     * @param connection Http connection
     */
    public HttpResponse(HttpConnection connection) {
        this.connection = connection;
        this.headers = new HashMap<>();
        this.bodyBuffer = ByteBuffer.allocate(WRITE_BUFFER_SIZE);
        responseCode = HttpResponseCode.HTTP_OK;
        headersSent = false;
        bodySize = 0;
    }

    /**
     * Obtains the response code of a response
     * @return int response code
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Set response code of a response
     * @param responseCode int response code
     */
    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    /**
     * Retrieve the headers of a response
     * @return Headers of the response
     */
    public Map<String,List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Add a new header to the response
     * @param headerName Header name
     * @param headerValue Header value
     */
    public void addHeader(String headerName, String headerValue) {
        List<String> headerValues = headers.get(headerName);
        if (headerValues == null) {
            headerValues = new ArrayList<>();
            headers.put(headerName, headerValues);
        }
        headerValues.add(headerValue);
    }

    /**
     * Retrieve the value of a header
     * @param headerName name of the header
     * @return value of the header
     */
    public String getHeader (String headerName) {
        String value = null;
        List<String> headerValues = headers.get(headerName);
        if (headerValues != null) {
            value = headerValues.get(0);
        }
        return value;
    }

    /**
     * Retrieve all the values for a given header
     * @param headerName name of the header
     * @return values for a header
     */
    public List<String> getHeaders (String headerName) {
        return headers.get(headerName);
    }

    /**
     * Remove all headers with a given name
     * @param headerName name of the header
     */
    public void removeHeader (String headerName) {
        headers.remove(headerName);
    }

    /**
     * Indicates if the response contains a given header
     * @param headerName name of the header
     * @return boolean
     */
    public boolean hasHeader (String headerName) {
        return headers.containsKey(headerName);
    }

    /**
     * Remove all headers
     */
    public void clearHeaders () {
        headers.clear();
    }

    /**
     * Adds a new cookie to the response
     * @param cookie cookie to add
     */
    public void addCookie (HttpCookie cookie) {
        StringBuilder cookieValue = new StringBuilder();
        cookieValue.append(cookie.getName());
        cookieValue.append("=");
        cookieValue.append(cookie.getValue());
        if (cookie.getExpires() != null) {
            cookieValue.append("; Expires=");
            cookieValue.append(HttpServerUtils.formatDate(cookie.getExpires()));
        }
        if (cookie.getMaxAge() != null) {
            cookieValue.append("; Max-Age=");
            cookieValue.append(cookie.getMaxAge());
        }
        if (cookie.getDomain() != null) {
            cookieValue.append("; Domain=").append(cookie.getDomain());
        }
        if (cookie.getPath() != null) {
            cookieValue.append("; Path=").append(cookie.getPath());
        }
        if (cookie.getSecure() != null) {
            cookieValue.append("; Secure");
        }
        if (cookie.getSecure() != null) {
            cookieValue.append("; HttpOnly");
        }
        addHeader(HttpHeader.SET_COOKIE, cookieValue.toString());
    }

    /**
     * Sets the content of the response
     * @param body content of the response
     */
    public void setBody(String body) {
        setBody(body.getBytes());
    }

    /**
     * Sets the content of the response
     * @param body content of the response
     */
    public void setBody(byte[] body) {
        write(body);
    }

    /**
     * Writes content in the response
     * @param text text to write in the response
     */
    public void write (String text) {
        write(text.getBytes());
    }

    /**
     * Write content in the response
     * @param bytes bytes to write in the response
     */
    public void write (byte[] bytes) {

        bodySize += bytes.length;
        int remainingBytes = bytes.length;
        int writeIndex = 0;
        while (remainingBytes > 0) {
            int remainingBufferBytes = bodyBuffer.remaining();
            if (remainingBytes > remainingBufferBytes) {
                bodyBuffer.put(bytes, writeIndex, remainingBufferBytes);
                writeBuffer();
                writeIndex += remainingBufferBytes;
                remainingBytes -= remainingBufferBytes;
            }
            else {
                bodyBuffer.put(bytes, writeIndex, remainingBytes);
                break;
            }
        }
    }

    /**
     * Flushes content in the response
     */
    public void flush () {
        writeBuffer();
    }

    /**
     * Send headers with the response
     */
    private void sendHeaders () {
        if (!headersSent) {
            addHeader(HttpHeader.DATE, HttpServerUtils.formatDate(new Date()));
            addHeader(HttpHeader.SERVER, HttpServer.SERVER_NAME);
            if (!connection.isAutoClose()) {
                addHeader(HttpHeader.CONNECTION, HttpHeader.KEEP_ALIVE);
            }
            else {
                addHeader(HttpHeader.CONNECTION, HttpHeader.CLOSE);
            }
            if (!hasHeader(HttpHeader.CONTENT_TYPE)) {
                addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_HTML);
            }
            if (!hasHeader(HttpHeader.CONTENT_LENGTH)) {
                addHeader(HttpHeader.CONTENT_LENGTH, String.valueOf(bodySize));
            }

            try {
                ByteBuffer headersBuffer = ByteBuffer.allocate(HEADERS_WRITE_BUFFER_SIZE);

                //Writing status line
                headersBuffer.put(MessageFormat.format(STATUS_LINE_TEMPLATE, responseCode, HttpResponseCode.msg(responseCode)).getBytes());
                headersBuffer.flip();
                connection.getChannel().write(headersBuffer);

                //Writing headers
                for (String headerName : headers.keySet()) {
                    List<String> headerValues = headers.get(headerName);
                    for (String headerValue : headerValues) {
                        headersBuffer.clear();
                        headersBuffer.put(MessageFormat.format(HEADER_LINE_TEMPLATE, headerName, headerValue).getBytes());
                        headersBuffer.flip();
                        connection.getChannel().write(headersBuffer);
                    }
                }

                //Writing separator
                headersBuffer.clear();
                headersBuffer.put(SEPARATOR.getBytes());
                headersBuffer.flip();
                connection.getChannel().write(headersBuffer);
            }
            catch (Throwable ex) {
                connection.close();
                throw new HttpException("Error writing headers !!", ex);
            }

            headersSent = true;
        }
    }

    /**
     * Writes the buffered content
     */
    protected void writeBuffer() {

        sendHeaders();
        bodyBuffer.flip();
        try {
            connection.getChannel().write(bodyBuffer);
        }
        catch (Exception ex) {
            connection.close();
            throw new HttpException("Error writing data !!", ex);
        }
        bodyBuffer.clear();
    }
}