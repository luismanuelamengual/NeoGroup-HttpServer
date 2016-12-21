package org.neogroup.net.httpserver;

import org.neogroup.utils.MimeTypes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {

    private final static String STATUS_LINE_TEMPLATE = "HTTP/1.1 {0} {1}\r\n";
    private final static String HEADER_LINE_TEMPLATE = "{0}: {1}\r\n";
    private final static String SEPARATOR = "\r\n";
    private final static int MAX_BUFFER_SIZE = 8 * 1024;

    private final OutputStream outputStream;
    private int responseCode;
    private Map<String, String> headers;
    private ByteArrayOutputStream body;
    private boolean headersSent;

    public HttpResponse(OutputStream outputStream) {
        this.outputStream = outputStream;
        this.responseCode = HttpResponseCode.HTTP_OK;
        this.headers = new HashMap<>();
        this.body = new ByteArrayOutputStream();
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
        this.body.reset();
        try { this.body.write(body); } catch (Exception ex) {}
    }

    public void write (String text) {
        write(text.getBytes());
    }

    public void write (byte[] bytes) {

        if ((bytes.length + body.size()) > MAX_BUFFER_SIZE) {
            flush();
        }
        try { this.body.write(bytes); } catch (Exception ex) {}
    }

    public void flush () {
        writeContents();
    }

    public void send () {
        flush();
        try { this.body.close(); } catch (Exception ex) {}
    }

    private void sendHeaders () {
        if (!headersSent) {
            if (!hasHeader(HttpHeader.CONTENT_TYPE)) {
                addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_HTML);
            }
            if (!hasHeader(HttpHeader.CONTENT_LENGTH)) {
                addHeader(HttpHeader.CONTENT_LENGTH, String.valueOf(body.size()));
            }
            addHeader(HttpHeader.DATE, HttpServerUtils.formatDate(new Date()));
            addHeader(HttpHeader.SERVER, HttpServer.SERVER_NAME);

            try {
                //Writing status line
                outputStream.write(MessageFormat.format(STATUS_LINE_TEMPLATE, responseCode, HttpResponseCode.msg(responseCode)).getBytes());

                //Writing headers
                for (String headerName : headers.keySet()) {
                    String headerValue = headers.get(headerName);
                    outputStream.write(MessageFormat.format(HEADER_LINE_TEMPLATE, headerName, headerValue).getBytes());
                }

                //Writing separator
                outputStream.write(SEPARATOR.getBytes());
            }
            catch (Throwable ex) {
                throw new HttpError("Error writing headers !!", ex);
            }

            headersSent = true;
        }
    }

    private void writeContents () {
        sendHeaders();
        try { body.writeTo(outputStream); } catch (IOException ex) {}
        try { outputStream.flush(); } catch (Exception ex) {}
        body.reset();
    }
}