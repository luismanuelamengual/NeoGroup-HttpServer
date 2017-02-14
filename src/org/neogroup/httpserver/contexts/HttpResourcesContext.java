
package org.neogroup.httpserver.contexts;

import org.neogroup.httpserver.HttpHeader;
import org.neogroup.httpserver.HttpRequest;
import org.neogroup.httpserver.HttpResponse;
import org.neogroup.httpserver.HttpResponseCode;
import org.neogroup.util.MimeTypes;
import org.neogroup.util.encoding.GZIPCompression;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class HttpResourcesContext extends HttpContext {

    private static final String DEFAULT_DIGEST_ENCRYPTION = "MD5";
    private static final String URI_FOLDER_SEPARATOR = "/";
    private static final String PACKAGE_SEPARATOR = ".";

    protected final String resourcePath;

    public HttpResourcesContext(String path) {
        this(path, "");
    }

    public HttpResourcesContext(String path, String basePackage) {
        super(path);
        String resourcePath = basePackage.replace(PACKAGE_SEPARATOR, File.separator);
        if (!resourcePath.isEmpty()) {
            resourcePath += File.separator;
        }
        this.resourcePath = resourcePath;
    }

    @Override
    public HttpResponse onContext(HttpRequest request) {

        String path = request.getPath().substring(getPath().length());
        String resourceName = resourcePath + path.replaceAll(URI_FOLDER_SEPARATOR, File.separator);

        HttpResponse response;
        byte[] resourceBytes = getResourceBytes(resourceName);
        if (resourceBytes != null) {
            response = handleResourceResponse(request, resourceBytes, MimeTypes.getMimeType(resourceName));
        } else {
            response = handleResourceNotFoundResponse(request, resourceName);
        }
        return response;
    }

    protected HttpResponse handleResourceNotFoundResponse (HttpRequest request, String resourceName) {

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
        response.setBody("Resource \"" + resourceName + "\" not found !!");
        return response;
    }

    protected byte[] getResourceBytes (String resourceName) {

        byte[] resourceBytes = null;
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (inputStream != null) {
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                resourceBytes = buffer.toByteArray();
            }
            catch (Exception ex) {}
        }
        return resourceBytes;
    }

    protected HttpResponse handleResourceResponse (HttpRequest request, byte[] resourceBytes, String mimeType) {

        String checksum = null;
        try {
            checksum = Base64.getEncoder().encodeToString(MessageDigest.getInstance(DEFAULT_DIGEST_ENCRYPTION).digest(resourceBytes));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Error obtaining resource checksum", ex);
        }

        int responseCode = HttpResponseCode.HTTP_OK;
        HttpResponse response = new HttpResponse();
        response.setResponseCode(responseCode);
        response.addHeader(HttpHeader.CONTENT_TYPE, mimeType);
        response.addHeader(HttpHeader.E_TAG, checksum);
        if (responseCode == HttpResponseCode.HTTP_OK) {
            String acceptedEncoding = request.getHeader(HttpHeader.ACCEPT_ENCODING);
            if (acceptedEncoding != null) {

                if (acceptedEncoding.indexOf(HttpHeader.GZIP_CONTENT_ENCODING) >= 0) {
                    try {
                        response.addHeader(HttpHeader.CONTENT_ENCODING, HttpHeader.GZIP_CONTENT_ENCODING);
                        response.addHeader(HttpHeader.VARY, HttpHeader.ACCEPT_ENCODING);
                        resourceBytes = GZIPCompression.compress(resourceBytes);
                    }
                    catch (IOException ex) {
                        throw new RuntimeException("Error compressing resource !!", ex);
                    }
                }
            }
            response.setBody(resourceBytes);
        }

        return response;
    }
}
