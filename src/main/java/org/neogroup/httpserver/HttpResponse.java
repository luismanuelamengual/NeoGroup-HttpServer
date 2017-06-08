
package org.neogroup.httpserver;

import org.neogroup.util.MimeUtils;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.*;

/**
 * This class holds information for a Http Response
 */
public class HttpResponse {

    private final HttpExchange exchange;

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
    protected HttpResponse(HttpConnection connection) {
        this.exchange = connection.getExchange();
    }

    /**
     * Obtains the response code of a response
     * @return int response code
     */
    public int getResponseCode() {
        return exchange.getResponseCode();
    }

    /**
     * Set response code of a response
     * @param responseCode int response code
     */
    public void setResponseCode(int responseCode) {
        exchange.setResponseCode(responseCode);
    }

    /**
     * Retrieve the responseHeaders of a response
     * @return Headers of the response
     */
    public Map<String, List<String>> getHeaders() {
        return exchange.getResponseHeaders();
    }

    /**
     * Add a new header to the response
     * @param headerName Header name
     * @param headerValue Header value
     */
    public void addHeader(String headerName, String headerValue) {
        exchange.addResponseHeader(headerName, headerValue);
    }

    /**
     * Retrieve the value of a header
     * @param headerName name of the header
     * @return value of the header
     */
    public String getHeader(String headerName) {
        return exchange.getResponseHeader(headerName);
    }

    /**
     * Retrieve all the values for a given header
     * @param headerName name of the header
     * @return values for a header
     */
    public List<String> getHeaders(String headerName) {
        return exchange.getResponseHeaders(headerName);
    }

    /**
     * Remove all responseHeaders with a given name
     * @param headerName name of the header
     */
    public void removeHeader(String headerName) {
        exchange.removeResponseHeader(headerName);
    }

    /**
     * Indicates if the response contains a given header
     * @param headerName name of the header
     * @return boolean
     */
    public boolean hasHeader(String headerName) {
        return exchange.hasResponseHeader(headerName);
    }

    /**
     * Remove all responseHeaders
     */
    public void clearHeaders() {
        exchange.clearResponseHeaders();
    }

    /**
     * Sets the content of the response
     * @param body content of the response
     */
    public void setBody(String body) {
        exchange.setResponseBody(body);
    }

    /**
     * Sets the content of the response
     * @param body content of the response
     */
    public void setBody(byte[] body) {
        exchange.setResponseBody(body);
    }

    /**
     * Writes content in the response
     * @param text text to write in the response
     */
    public void write(String text) {
        exchange.write(text);
    }

    /**
     * Write content in the response
     * @param bytes bytes to write in the response
     */
    public void write(byte[] bytes) {
        exchange.write(bytes);
    }

    /**
     * Flushes content in the response
     */
    public void flush() {
        exchange.flush();
    }

    /**
     * Adds a new cookie to the response
     * @param cookie cookie to add
     */
    public void addCookie(HttpCookie cookie) {
        exchange.addCookie(cookie);
    }

    /**
     * Retrieve the cookies of a request
     * @return list of cookies
     */
    public Collection<HttpCookie> getCookies() {
        return exchange.getCookies();
    }

    /**
     * Obtain a cookie by its name
     * @param cookieName name of cookie
     * @return http cookie
     */
    public HttpCookie getCookie(String cookieName) {
        return exchange.getCookie(cookieName);
    }
}