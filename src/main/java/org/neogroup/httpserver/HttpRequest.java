
package org.neogroup.httpserver;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Class that holds the information of a request
 */
public class HttpRequest {

    private static final String QUERY_PARAMETERS_REGEX = "[&]";
    private static final String QUERY_PARAMETER_VALUES_REGEX = "[=]";
    private static final String FILE_ENCODING_SYSTEM_PROPERTY_NAME = "file.encoding";
    private static final String URI_SEPARATOR = "/";
    private static final int READ_BUFFER_SIZE = 2048;
    private static final byte LINE_SEPARATOR_CR = '\r';
    private static final byte LINE_SEPARATOR_LF = '\n';
    private static final String LINE_FIELD_SEPARATOR = " ";
    private static final int HEADER_SEPARATOR = ':';
    private static final int METHOD_INDEX = 0;
    private static final int REQUEST_PATH_INDEX = 1;
    private static final int VERSION_INDEX = 2;

    private final HttpConnection connection;
    private String method;
    private URI uri;
    private String version;
    private Map<String, List<String>> headers;
    private Map<String,String> parameters;
    private boolean parametersParsed;
    private byte[] body;

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
    public HttpRequest (HttpConnection connection) {
        this.connection = connection;
        this.headers = new HashMap<>();
        this.parameters = new HashMap<>();
        parametersParsed = false;

        //Leer una peticion nueva
        byte[] readData = null;
        try (ByteArrayOutputStream readStream = new ByteArrayOutputStream()) {
            int totalReadSize = 0;
            int readSize = 0;
            ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
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
            connection.close();
            throw new HttpException("Error reading request !!", ex);
        }

        //Parsear la nueva petición
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
                                body = Arrays.copyOfRange(readData, i + 2, readData.length);
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
    }

    /**
     * Parses a status line
     * @param statusLine String with the status line
     * @throws Exception
     */
    private void processStatusLine (String statusLine) throws Exception {

        String[] parts = statusLine.split(LINE_FIELD_SEPARATOR);
        method = parts[METHOD_INDEX];
        uri = new URI(parts[REQUEST_PATH_INDEX]);
        version = parts[VERSION_INDEX];
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
        List<String> headerValues = headers.get(headerName);
        if (headerValues == null) {
            headerValues = new ArrayList<>();
            headers.put(headerName, headerValues);
        }
        headerValues.add(headerValue);
    }

    /**
     * Retrieves the method of the request
     * @return method
     */
    public String getMethod() {
        return method;
    }

    /**
     * Retrieves the uri of the request
     * @return uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Retrieves the query of the request
     * @return query
     */
    public String getQuery() {
        return uri.getRawQuery();
    }

    /**
     * Retrieves the path of the request
     * @return path
     */
    public String getPath() {
        return uri.getRawPath();
    }

    /**
     * Retrieve the path parts
     * @return array of path parts
     */
    public List<String> getPathParts() {
        String path = getPath();
        String[] pathTokens = path.split(URI_SEPARATOR);
        return Arrays.asList(pathTokens);
    }

    /**
     * Retrieves the version of the request
     * @return version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Retrieve the headers of the request
     * @return headers
     */
    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Retrieve all the headers for a given header name
     * @param headerName name of header
     * @return List of header values
     */
    public List<String> getHeaders (String headerName) {
        return headers.get(headerName);
    }

    /**
     * Indicates if a header name exists or not
     * @param headerName name of the header
     * @return boolean
     */
    public boolean hasHeader (String headerName) {
        return headers.containsKey(headerName);
    }

    /**
     * Retrieve the first header value for the given header name
     * @param headerName name of header
     * @return header value
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
     * Retrieve the cookies of a request
     * @return list of cookies
     */
    public List<HttpCookie> getCookies () {
        List<HttpCookie> cookies = new ArrayList<>();
        String cookieHeader = getHeader(HttpHeader.COOKIE);
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
     * Retrieves the body of a request
     * @return body
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * Retrieve the http parameters of a request
     * @return map of parameters
     */
    public Map<String,String> getParameters() {

        if (!parametersParsed) {
            addParametersFromQuery(getQuery());
            String requestContentType = getHeader(HttpHeader.CONTENT_TYPE);
            if (requestContentType != null && requestContentType.equals(HttpHeader.APPLICATION_FORM_URL_ENCODED)) {
                addParametersFromQuery(new String(getBody()));
            }
            parametersParsed = true;
        }
        return parameters;
    }

    /**
     * Retrieve the value of a parameter
     * @param name name of a parameter
     * @return value of a parameter
     */
    public String getParameter (String name) {
        return getParameters().get(name);
    }

    /**
     * Indicates if a parameter exists or not
     * @param name name of the parameter to check
     * @return boolean
     */
    public boolean hasParameter (String name) {
        return getParameters().containsKey(name);
    }

    /**
     * Retrieve parameters from a query string
     * @param query query string
     */
    private void addParametersFromQuery(String query) {

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
            connection.close();
            throw new HttpException("Error reading request parameters !!", ex);
        }
    }
}