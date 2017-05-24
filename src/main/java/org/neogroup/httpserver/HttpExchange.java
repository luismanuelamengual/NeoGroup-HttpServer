
package org.neogroup.httpserver;

import org.neogroup.util.MimeUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.*;

public class HttpExchange {

    private static final String QUERY_PARAMETERS_REGEX = "[&]";
    private static final String QUERY_PARAMETER_VALUES_REGEX = "[=]";
    private static final String FILE_ENCODING_SYSTEM_PROPERTY_NAME = "file.encoding";
    private static final String URI_SEPARATOR = "/";
    private static final int REQUEST_READ_BUFFER_SIZE = 2048;
    private static final byte LINE_SEPARATOR_CR = '\r';
    private static final byte LINE_SEPARATOR_LF = '\n';
    private static final String STATUS_LINE_FIELD_SEPARATOR = " ";
    private static final String STATUS_LINE_TEMPLATE = "HTTP/1.1 {0} {1}\r\n";
    private static final String HEADER_LINE_TEMPLATE = "{0}: {1}\r\n";
    private static final int HEADER_SEPARATOR = ':';
    private static final int HEADERS_WRITE_BUFFER_SIZE = 2048;
    private static final int BODY_WRITE_BUFFER_SIZE = 8192;

    private final HttpConnection connection;
    private HttpSession session;

    private String requestMethod;
    private URI requestUri;
    private String requestVersion;
    private Map<String, List<String>> requestHeaders;
    private Map<String,String> requestParameters;
    private byte[] requestBody;

    private int responseCode;
    private Map<String, List<String>> responseHeaders;
    private ByteBuffer responseBodyBuffer;
    private int responseBodySize;
    private boolean responseHeadersSent;

    /**
     * Constructor for the http exchange
     * @param connection connection associated with the exchange
     */
    protected HttpExchange(HttpConnection connection) {

        this.connection = connection;
        this.requestHeaders = new LinkedHashMap<>();
        this.responseHeaders = new LinkedHashMap<>();
        this.responseBodyBuffer = ByteBuffer.allocate(BODY_WRITE_BUFFER_SIZE);
    }

    /**
     * Starts the new http exchange
     */
    protected void startNewExchange() throws HttpBadRequestException {

        //Clear exchange values
        requestMethod = null;
        requestUri = null;
        requestVersion = null;
        requestHeaders.clear();
        requestParameters = null;
        requestBody = null;
        responseHeaders.clear();
        responseCode = HttpResponseCode.HTTP_OK;
        responseBodyBuffer.clear();
        responseHeadersSent = false;
        responseBodySize = 0;

        //Read request
        byte[] readData = null;
        try (ByteArrayOutputStream readStream = new ByteArrayOutputStream()) {
            int totalReadSize = 0;
            int readSize = 0;
            ByteBuffer readBuffer = ByteBuffer.allocate(REQUEST_READ_BUFFER_SIZE);
            do {
                readSize = connection.getChannel().read(readBuffer);
                if (readSize == -1) {
                    throw new HttpException("Socket closed !!");
                }
                if (readSize > 0) {
                    readStream.write(readBuffer.array(), 0, readSize);
                    readBuffer.rewind();
                    totalReadSize += readSize;
                }
            } while (readSize > 0);

            if (totalReadSize > 0) {
                readStream.flush();
                readData = readStream.toByteArray();
            }
        }
        catch (Exception ex) {
            throw new HttpBadRequestException("Error reading request !!", ex);
        }

        if (readData != null) {
            boolean processedStatusLine = false;
            boolean processedRequest = false;
            try {

                int startLineIndex = 0;
                for (int i = 0; i < readData.length - 1; i++) {

                    if (readData[i] == LINE_SEPARATOR_CR && readData[i + 1] == LINE_SEPARATOR_LF) {
                        String currentLine = new String(readData, startLineIndex, i - startLineIndex);
                        startLineIndex = i + 2;

                        if (!currentLine.isEmpty()) {
                            if (!processedStatusLine) {
                                processStatusLine(currentLine);
                                processedStatusLine = true;
                            } else {
                                processHeaderLine(currentLine);
                            }
                        } else {
                            if (processedStatusLine) {
                                int bodySize = readData.length - (i + 2);
                                requestBody = Arrays.copyOfRange(readData, i + 2, readData.length);
                                processedRequest = true;
                            }
                            break;
                        }
                    }
                }
            }
            catch (Exception exception) {
                throw new HttpBadRequestException("Malformed request !!", exception);
            }
            if (!processedRequest) {
                throw new HttpBadRequestException("Incomplete request !!");
            }
        }
        else {
            throw new HttpBadRequestException("Empty request !!");
        }

        //Get the session for the new request
        HttpSessionManager sessionManager = connection.getServer().getSessionManager();
        if (sessionManager != null) {
            session = sessionManager.getSession(connection);
        }
    }

    /**
     * Parses a status line
     * @param statusLine String with the status line
     * @throws Exception
     */
    private void processStatusLine (String statusLine) throws Exception {

        String[] parts = statusLine.split(STATUS_LINE_FIELD_SEPARATOR);
        requestMethod = parts[0];
        requestUri = new URI(parts[1]);
        requestVersion = parts[2];
    }

    /**
     * Parses the header line
     * @param headerLine String with the header line
     * @throws Exception
     */
    private void processHeaderLine (String headerLine) throws Exception {

        int separatorIndex = headerLine.indexOf(HEADER_SEPARATOR);
        String headerName = headerLine.substring(0, separatorIndex);
        String headerValue = headerLine.substring(separatorIndex+1).trim();
        List<String> headerValues = requestHeaders.get(headerName);
        if (headerValues == null) {
            headerValues = new ArrayList<>();
            requestHeaders.put(headerName, headerValues);
        }
        headerValues.add(headerValue);
    }

    /**
     * Retrieves the requestMethod of the request
     * @return requestMethod
     */
    public String getRequestMethod() {
        return requestMethod;
    }

    /**
     * Retrieves the requestUri of the request
     * @return requestUri
     */
    public URI getRequestUri() {
        return requestUri;
    }

    /**
     * Retrieves the query of the request
     * @return query
     */
    public String getRequestQuery() {
        return requestUri.getRawQuery();
    }

    /**
     * Retrieves the path of the request
     * @return path
     */
    public String getRequestPath() {
        return requestUri.getRawPath();
    }

    /**
     * Retrieve the path parts
     * @return array of path parts
     */
    public List<String> getRequestPathParts() {
        String path = getRequestPath();
        String[] pathTokens = path.split(URI_SEPARATOR);
        return Arrays.asList(pathTokens);
    }

    /**
     * Retrieves the requestVersion of the request
     * @return requestVersion
     */
    public String getRequestVersion() {
        return requestVersion;
    }

    /**
     * Retrieve the requestHeaders of the request
     * @return requestHeaders
     */
    public Map<String, List<String>> getRequestHeaders() {
        return Collections.unmodifiableMap(requestHeaders);
    }

    /**
     * Retrieve all the requestHeaders for a given header name
     * @param headerName name of header
     * @return List of header values
     */
    public List<String> getRequestHeaders (String headerName) {
        return requestHeaders.get(headerName);
    }

    /**
     * Indicates if a header name exists or not
     * @param headerName name of the header
     * @return boolean
     */
    public boolean hasRequestHeader (String headerName) {
        return requestHeaders.containsKey(headerName);
    }

    /**
     * Retrieve the first header value for the given header name
     * @param headerName name of header
     * @return header value
     */
    public String getRequestHeader (String headerName) {
        String value = null;
        List<String> headerValues = requestHeaders.get(headerName);
        if (headerValues != null) {
            value = headerValues.get(0);
        }
        return value;
    }

    /**
     * Retrieves the requestBody of a request
     * @return requestBody
     */
    public byte[] getRequestBody() {
        return requestBody;
    }

    /**
     * Retrieve the http requestParameters of a request
     * @return map of requestParameters
     */
    public Map<String,String> getRequestParameters() {

        if (requestParameters == null) {
            requestParameters = new HashMap<>();
            addParametersFromQuery(requestParameters, getRequestQuery());
            String requestContentType = getRequestHeader(HttpHeader.CONTENT_TYPE);
            if (requestContentType != null && requestContentType.equals(HttpHeader.APPLICATION_FORM_URL_ENCODED)) {
                addParametersFromQuery(requestParameters, new String(getRequestBody()));
            }
        }
        return requestParameters;
    }

    /**
     * Retrieve the value of a parameter
     * @param name name of a parameter
     * @return value of a parameter
     */
    public String getRequestParameter (String name) {
        return getRequestParameters().get(name);
    }

    /**
     * Indicates if a parameter exists or not
     * @param name name of the parameter to check
     * @return boolean
     */
    public boolean hasRequestParameter (String name) {
        return getRequestParameters().containsKey(name);
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
     * Retrieve the responseHeaders of a response
     * @return Headers of the response
     */
    public Map<String,List<String>> getResponseHeaders() {
        return Collections.unmodifiableMap(responseHeaders);
    }

    /**
     * Add a new header to the response
     * @param headerName Header name
     * @param headerValue Header value
     */
    public void addResponseHeader(String headerName, String headerValue) {
        List<String> headerValues = responseHeaders.get(headerName);
        if (headerValues == null) {
            headerValues = new ArrayList<>();
            responseHeaders.put(headerName, headerValues);
        }
        headerValues.add(headerValue);
    }

    /**
     * Retrieve the value of a header
     * @param headerName name of the header
     * @return value of the header
     */
    public String getResponseHeader (String headerName) {
        String value = null;
        List<String> headerValues = responseHeaders.get(headerName);
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
    public List<String> getResponseHeaders (String headerName) {
        return responseHeaders.get(headerName);
    }

    /**
     * Remove all responseHeaders with a given name
     * @param headerName name of the header
     */
    public void removeResponseHeader (String headerName) {
        responseHeaders.remove(headerName);
    }

    /**
     * Indicates if the response contains a given header
     * @param headerName name of the header
     * @return boolean
     */
    public boolean hasResponseHeader (String headerName) {
        return responseHeaders.containsKey(headerName);
    }

    /**
     * Remove all responseHeaders
     */
    public void clearResponseHeaders () {
        responseHeaders.clear();
    }

    /**
     * Sets the content of the response
     * @param body content of the response
     */
    public void setResponseBody(String body) {
        setResponseBody(body.getBytes());
    }

    /**
     * Sets the content of the response
     * @param body content of the response
     */
    public void setResponseBody(byte[] body) {
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

        responseBodySize += bytes.length;
        int remainingBytes = bytes.length;
        int writeIndex = 0;
        while (remainingBytes > 0) {
            int remainingBufferBytes = responseBodyBuffer.remaining();
            if (remainingBytes > remainingBufferBytes) {
                responseBodyBuffer.put(bytes, writeIndex, remainingBufferBytes);
                writeBuffer();
                writeIndex += remainingBufferBytes;
                remainingBytes -= remainingBufferBytes;
            }
            else {
                responseBodyBuffer.put(bytes, writeIndex, remainingBytes);
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
        addResponseHeader(HttpHeader.SET_COOKIE, cookieValue.toString());
    }

    /**
     * Retrieve the cookies of a request
     * @return list of cookies
     */
    public List<HttpCookie> getCookies () {
        List<HttpCookie> cookies = new ArrayList<>();
        String cookieHeader = getRequestHeader(HttpHeader.COOKIE);
        if (cookieHeader != null) {
            String[] cookieHeaderTokens = cookieHeader.split(";");
            for (String cookieHeaderToken : cookieHeaderTokens) {
                String[] cookieParts = cookieHeaderToken.trim().split("=");
                String cookieName = cookieParts[0];
                String cookieValue = cookieParts[1];
                cookies.add(new HttpCookie(cookieName, cookieValue));
            }
        }
        return cookies;
    }

    /**
     * Get the current session associated with the exchange
     * @return http session
     */
    public HttpSession getSession() {
        return session;
    }

    /**
     * Creates a new session
     * @return http session
     */
    public HttpSession createSession() {
        session = null;
        HttpSessionManager sessionManager = connection.getServer().getSessionManager();
        if (sessionManager != null) {
            session = sessionManager.createSession(connection);
        }
        return session;
    }

    /**
     * Destroys the current session
     * @return http session
     */
    public HttpSession destroySession() {
        HttpSession destroyedSession = null;
        if (session != null) {
            HttpSessionManager sessionManager = connection.getServer().getSessionManager();
            if (sessionManager != null) {
                destroyedSession = sessionManager.destroySession(connection);
            }
        }
        session = null;
        return destroyedSession;
    }

    /**
     * Send responseHeaders with the response
     */
    private void sendHeaders () {
        if (!responseHeadersSent) {

            if (!hasResponseHeader(HttpHeader.CONTENT_TYPE)) {
                addResponseHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_PLAIN);
            }
            if (!hasResponseHeader(HttpHeader.CONTENT_LENGTH)) {
                addResponseHeader(HttpHeader.CONTENT_LENGTH, String.valueOf(responseBodySize));
            }

            try {
                ByteBuffer headersBuffer = ByteBuffer.allocate(HEADERS_WRITE_BUFFER_SIZE);

                //Writing status line
                headersBuffer.put(MessageFormat.format(STATUS_LINE_TEMPLATE, responseCode, HttpResponseCode.msg(responseCode)).getBytes());
                headersBuffer.flip();
                connection.getChannel().write(headersBuffer);

                //Writing responseHeaders
                for (String headerName : responseHeaders.keySet()) {
                    List<String> headerValues = responseHeaders.get(headerName);
                    for (String headerValue : headerValues) {
                        headersBuffer.clear();
                        headersBuffer.put(MessageFormat.format(HEADER_LINE_TEMPLATE, headerName, headerValue).getBytes());
                        headersBuffer.flip();
                        connection.getChannel().write(headersBuffer);
                    }
                }

                //Writing separator
                headersBuffer.clear();
                headersBuffer.put(LINE_SEPARATOR_CR);
                headersBuffer.put(LINE_SEPARATOR_LF);
                headersBuffer.flip();
                connection.getChannel().write(headersBuffer);
            }
            catch (Throwable ex) {
                throw new HttpException("Error writing responseHeaders !!", ex);
            }

            responseHeadersSent = true;
        }
    }

    /**
     * Writes the buffered content
     */
    private void writeBuffer() {

        sendHeaders();
        responseBodyBuffer.flip();
        try {
            connection.getChannel().write(responseBodyBuffer);
        }
        catch (Exception ex) {
            throw new HttpException("Error writing data !!", ex);
        }
        responseBodyBuffer.clear();
    }

    /**
     * Retrieve requestParameters from a query string
     * @param query query string
     */
    private void addParametersFromQuery(Map<String,String> parameters, String query) {

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
            throw new HttpException("Error reading request requestParameters !!", ex);
        }
    }
}
