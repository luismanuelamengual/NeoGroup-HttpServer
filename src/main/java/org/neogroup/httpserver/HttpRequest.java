
package org.neogroup.httpserver;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.*;

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
    private Map<String, String> headers;
    private Map<String,String> parameters;
    private boolean parametersParsed;
    private byte[] body;

    public HttpRequest () {
        this(HttpConnection.getActiveConnection());
    }

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

    private void processStatusLine (String statusLine) throws Exception {

        String[] parts = statusLine.split(LINE_FIELD_SEPARATOR);
        method = parts[METHOD_INDEX];
        uri = new URI(parts[REQUEST_PATH_INDEX]);
        version = parts[VERSION_INDEX];
    }

    private void processHeaderLine (String headerLine) throws Exception {

         int separatorIndex = headerLine.indexOf(HEADER_SEPARATOR);
         headers.put(headerLine.substring(0, separatorIndex), headerLine.substring(separatorIndex+1).trim());
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
        return body;
    }

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

    public String getParameter (String name) {
        return getParameters().get(name);
    }

    public boolean hasParameter (String name) {
        return getParameters().containsKey(name);
    }

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