package org.neogroup.net.httpserver;

import org.neogroup.utils.MimeTypes;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {

    private final static String STATUS_LINE_TEMPLATE = "HTTP/1.1 {0} {1}\r\n";
    private final static String HEADER_LINE_TEMPLATE = "{0}: {1}\r\n";
    private final static String SEPARATOR = "\r\n";
    private final static int WRITE_BUFFER_SIZE = 8 * 1024;
    private final static int HEADERS_WRITE_BUFFER_SIZE = 2048;

    private final SocketChannel channel;
    private int responseCode;
    private Map<String, String> headers;
    private ByteBuffer bodyBuffer;
    private int bodySize;
    private boolean headersSent;

    public HttpResponse(SocketChannel channel) {
        this.channel = channel;
        this.responseCode = HttpResponseCode.HTTP_OK;
        this.headers = new HashMap<>();
        this.bodyBuffer = ByteBuffer.allocate(WRITE_BUFFER_SIZE);
        this.bodySize = 0;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public Map<String,String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public void addHeader(String headerName, String headerValue) {
        this.headers.put(headerName, headerValue);
    }

    public String getHeader (String headerName) {
        return headers.get(headerName);
    }

    public void removeHeader (String headerName) {
        headers.remove(headerName);
    }

    public boolean hasHeader (String headerName) {
        return headers.containsKey(headerName);
    }

    public void clearHeaders () {
        headers.clear();
    }

    public void setBody(String body) {
        setBody(body.getBytes());
    }

    public void setBody(byte[] body) {
        write(body);
    }

    public void write (String text) {
        write(text.getBytes());
    }

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

    public void flush () {
        writeBuffer();
    }

    public void sendResponse () {
        flush();
    }

    private void sendHeaders () {
        if (!headersSent) {
            if (!hasHeader(HttpHeader.CONTENT_TYPE)) {
                addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_HTML);
            }
            if (!hasHeader(HttpHeader.CONTENT_LENGTH)) {
                addHeader(HttpHeader.CONTENT_LENGTH, String.valueOf(bodySize));
            }

            try {
                ByteBuffer headersBuffer = ByteBuffer.allocate(HEADERS_WRITE_BUFFER_SIZE);

                //Writing status line
                headersBuffer.put(MessageFormat.format(STATUS_LINE_TEMPLATE, responseCode, HttpResponseCode.msg(responseCode)).getBytes());
                headersBuffer.flip();
                channel.write(headersBuffer);

                //Writing headers
                for (String headerName : headers.keySet()) {
                    String headerValue = headers.get(headerName);
                    headersBuffer.clear();
                    headersBuffer.put(MessageFormat.format(HEADER_LINE_TEMPLATE, headerName, headerValue).getBytes());
                    headersBuffer.flip();
                    channel.write(headersBuffer);
                }

                //Writing separator
                headersBuffer.clear();
                headersBuffer.put(SEPARATOR.getBytes());
                headersBuffer.flip();
                channel.write(headersBuffer);
            }
            catch (Throwable ex) {
                throw new HttpError("Error writing headers !!", ex);
            }

            headersSent = true;
        }
    }

    private void writeBuffer() {

        sendHeaders();
        bodyBuffer.flip();
        try {
            channel.write(bodyBuffer);
        }
        catch (Exception ex) {
            throw new HttpError ("Error writing data !!", ex);
        }
        bodyBuffer.clear();
    }
}