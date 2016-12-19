package org.neogroup.net.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {

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
        sendHeaders(0);
        writeContents();
    }

    private void sendHeaders () {
        sendHeaders(body.size() > 0? body.size() : -1);
    }

    private void sendHeaders (long contentLength) {
        if (!headersSent) {
            /*if (!getHeaders().containsKey(HttpHeader.CONTENT_TYPE)) {
                addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_HTML);
            }*/
            addHeader(HttpHeader.DATE, HttpServerUtils.formatDate(new Date()));
            addHeader(HttpHeader.SERVER, HttpServer.SERVER_NAME);

            //TODO: Escribir las cabeceras y el Content-Length
            headersSent = true;
        }
    }

    private void writeContents () {
        try { body.writeTo(outputStream); } catch (IOException ex) {}
        try { outputStream.flush(); } catch (Exception ex) {}
        body.reset();
    }
}