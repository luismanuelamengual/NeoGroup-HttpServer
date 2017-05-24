
package org.neogroup.httpserver;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Class that holds the information of a request
 */
public class HttpRequest {

    private final HttpConnection connection;
    private final HttpExchange exchange;

    /**
     * Constructor for a http request
     */
    public HttpRequest () {
        this(HttpServer.getCurrentThreadConnection());
    }

    /**
     * Constructor for a http request with a given connection
     * @param connection
     */
    protected HttpRequest (HttpConnection connection) {
        this.connection = connection;
        this.exchange = connection.getExchange();
    }

    /**
     * Retrieves the method of the request
     * @return request Method
     */
    public String getMethod() {
        return exchange.getRequestMethod();
    }

    /**
     * Retrieves the uri of the request
     * @return requestUri
     */
    public URI getUri() {
        return exchange.getRequestUri();
    }

    /**
     * Retrieves the query of the request
     * @return query
     */
    public String getQuery() {
        return exchange.getRequestQuery();
    }

    /**
     * Retrieves the path of the request
     * @return path
     */
    public String getPath() {
        return exchange.getRequestPath();
    }

    /**
     * Retrieve the path parts
     * @return array of path parts
     */
    public List<String> getRequestPathParts() {
        return exchange.getRequestPathParts();
    }

    /**
     * Retrieves the requestVersion of the request
     * @return requestVersion
     */
    public String getVersion() {
        return exchange.getRequestVersion();
    }

    /**
     * Retrieve the requestHeaders of the request
     * @return requestHeaders
     */
    public Map<String, List<String>> getHeaders() {
        return exchange.getRequestHeaders();
    }

    /**
     * Retrieve all the requestHeaders for a given header name
     * @param headerName name of header
     * @return List of header values
     */
    public List<String> getHeaders(String headerName) {
        return exchange.getRequestHeaders(headerName);
    }

    /**
     * Indicates if a header name exists or not
     * @param headerName name of the header
     * @return boolean
     */
    public boolean hasHeader(String headerName) {
        return exchange.hasRequestHeader(headerName);
    }

    /**
     * Retrieve the first header value for the given header name
     * @param headerName name of header
     * @return header value
     */
    public String getHeader(String headerName) {
        return exchange.getRequestHeader(headerName);
    }

    /**
     * Retrieves the requestBody of a request
     * @return requestBody
     */
    public byte[] getBody() {
        return exchange.getRequestBody();
    }

    /**
     * Retrieve the http requestParameters of a request
     * @return map of requestParameters
     */
    public Map<String, String> getParameters() {
        return exchange.getRequestParameters();
    }

    /**
     * Retrieve the value of a parameter
     * @param name name of a parameter
     * @return value of a parameter
     */
    public String getParameter(String name) {
        return exchange.getRequestParameter(name);
    }

    /**
     * Indicates if a parameter exists or not
     * @param name name of the parameter to check
     * @return boolean
     */
    public boolean hasParameter(String name) {
        return exchange.hasRequestParameter(name);
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

    /**
     * Get the current session associated with the exchange
     * @return http session
     */
    public HttpSession getSession() {
        return exchange.getSession();
    }

    /**
     * Creates a new session
     * @return http session
     */
    public HttpSession createSession() {
        return exchange.createSession();
    }

    /**
     * Destroys the current session
     * @return http session
     */
    public HttpSession destroySession() {
        return exchange.destroySession();
    }
}