
package org.neogroup.net.httpserver;

import org.neogroup.util.encoding.GZIPCompression;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

public abstract class HttpAbstractResourcesContext extends HttpContext {

    private static final String DEFAULT_DIGEST_ENCRYPTION = "MD5";
    private static final String URI_FOLDER_SEPARATOR = "/";

    protected final String resourcesPath;

    public HttpAbstractResourcesContext(String path, String resourcesPath) {
        super(path);
        this.resourcesPath = resourcesPath;
    }

    @Override
    public void onContext(HttpRequest request, HttpResponse response) {

        String path = request.getPath().substring(getPath().length());
        String resourceName = resourcesPath + File.separator +path.replaceAll(URI_FOLDER_SEPARATOR, File.separator);
        onResourceRequest (request, response, resourceName);
    }

    protected void handleResourceNotFoundResponse (HttpRequest request, HttpResponse response, String resourceName) {

        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
        response.setBody("Resource \"" + resourceName + "\" not found !!");
    }

    protected void handleResourceResponse (HttpRequest request, HttpResponse response, byte[] resourceBytes, String mimeType, Date lastModifiedDate) {

        String checksum = null;
        try {
            checksum = Base64.getEncoder().encodeToString(MessageDigest.getInstance(DEFAULT_DIGEST_ENCRYPTION).digest(resourceBytes));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Error obtaining file checksum", ex);
        }

        int responseCode = HttpResponseCode.HTTP_OK;
        String modifiedSinceHeader = request.getHeader(HttpHeader.IF_MODIFIED_SINCE);
        if (modifiedSinceHeader != null && lastModifiedDate != null) {
            Date modifiedSinceDate = null;
            try {
                modifiedSinceDate = HttpServerUtils.getDate(modifiedSinceHeader);
                if (!lastModifiedDate.after(modifiedSinceDate)) {
                    responseCode = HttpResponseCode.HTTP_NOT_MODIFIED;
                }
            }
            catch (ParseException ex) {}
        }
        else {
            String nonModifiedChecksum = request.getHeader(HttpHeader.IF_NONE_MATCH);
            if (nonModifiedChecksum != null) {
                if (checksum.equals(nonModifiedChecksum)) {
                    responseCode = HttpResponseCode.HTTP_NOT_MODIFIED;
                }
            }
        }

        response.setResponseCode(responseCode);
        response.addHeader(HttpHeader.CONTENT_TYPE, mimeType);
        response.addHeader(HttpHeader.E_TAG, checksum);
        if (lastModifiedDate != null) {
            response.addHeader(HttpHeader.LAST_MODIFIED, HttpServerUtils.formatDate(lastModifiedDate));
        }

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
                        throw new RuntimeException("Error compressing file !!", ex);
                    }
                }
            }
            response.setBody(resourceBytes);
        }
    }

    protected abstract void onResourceRequest(HttpRequest request, HttpResponse response, String resourceName);
}
