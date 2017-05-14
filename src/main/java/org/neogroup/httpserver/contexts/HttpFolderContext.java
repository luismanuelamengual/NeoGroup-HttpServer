
package org.neogroup.httpserver.contexts;

import org.neogroup.httpserver.*;
import org.neogroup.util.MimeUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

/**
 * Context to access to access files
 */
public class HttpFolderContext extends HttpContext {

    private static final String CLASS_PATH_PREFIX = "${classPath}";
    private static final String CURRENT_PATH_PREFIX = "${currentPath}";
    private static final String DEFAULT_DIGEST_ENCRYPTION = "MD5";
    private static final String URI_FOLDER_SEPARATOR = "/";
    private static final String FOLDER_HTML_DOCUMENT_TEMPLATE = "<!DOCTYPE html><html><head><title>%s</title><body>%s</body></html></head>";
    private static final String FOLDER_HTML_LIST_TEMPLATE = "<ul style=\"list-style-type: none;\">%s</ul>";
    private static final String FOLDER_HTML_ITEM_TEMPLATE = "<li><a href=\"%s\">%s</a></li>";

    protected final String folder;
    protected final boolean isClasspathFolder;

    /**
     * Constructor of the folder context
     * @param path Path to access the context
     * @param folder Folder that is accessed via the context
     */
    public HttpFolderContext(String path, String folder) {
        super(path);
        if (folder.startsWith(CLASS_PATH_PREFIX)) {
            this.isClasspathFolder = true;
            this.folder = folder.substring(CLASS_PATH_PREFIX.length() + 1);
        }
        else {
            this.isClasspathFolder = false;
            if (folder.startsWith(CURRENT_PATH_PREFIX)) {
                this.folder = Paths.get("").toAbsolutePath().toString() + folder.substring(CURRENT_PATH_PREFIX.length());
            }
            else {
                this.folder = folder;
            }
        }
    }

    /**
     * Method that is executed when accesing the context
     * @param request Http request
     * @return HttpResponse response
     */
    @Override
    public HttpResponse onContext(HttpRequest request) {

        String path = request.getPath().substring(getPath().length());
        String fileName = folder + path.replaceAll(URI_FOLDER_SEPARATOR, File.separator);

        HttpResponse response;
        if (isClasspathFolder) {
            byte[] resourceBytes = getResourceBytes(fileName);
            if (resourceBytes != null) {
                response = handleFileResponse(request, resourceBytes, MimeUtils.getMimeType(fileName), null);
            } else {
                response = handleResourceNotFoundResponse(request, fileName);
            }
        }
        else {
            File file = new File(fileName);
            if (file.exists()) {
                if (file.isDirectory()) {
                    response = handleDirectoryResponse(request, file);
                } else {
                    response = handleFileResponse(request, file);
                }
            } else {
                response = handleFileNotFoundResponse(request, file);
            }
        }
        return response;
    }

    /**
     * Handles a directory response
     * @param request Http request
     * @param file File pointing to a directory
     * @return HttpResponse the response
     */
    protected HttpResponse handleDirectoryResponse (HttpRequest request, File file) {

        StringBuilder list = new StringBuilder();
        Path filePath = file.toPath();
        Path baseFilePath = Paths.get(folder);
        File[] subFiles = file.listFiles();
        Arrays.sort(subFiles);
        for (File subFile : subFiles) {
            String subFileLink = filePath.relativize(baseFilePath).resolve(request.getPath()).resolve(subFile.getName()).toString();
            String subFileName = subFile.getName();
            if (subFile.isDirectory()) {
                subFileName += File.separator;
            }
            list.append(String.format(FOLDER_HTML_ITEM_TEMPLATE, subFileLink, subFileName));
        }
        String htmlBody = String.format(FOLDER_HTML_LIST_TEMPLATE, list.toString());
        String document = String.format(FOLDER_HTML_DOCUMENT_TEMPLATE, file.getName(), htmlBody);

        HttpResponse response = new HttpResponse();
        response.addHeader(HttpHeader.CONTENT_TYPE, MimeUtils.TEXT_HTML);
        response.setBody(document.getBytes());
        return response;
    }

    /**
     * Retrieves the bytes of a resource
     * @param resourceName name of the resource
     * @return byte[] bytes of the resource
     */
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

    /**
     * Handles when a resource is not found
     * @param request Http Request
     * @param resourceName the resource name
     * @return HttpResponse the response
     */
    protected HttpResponse handleResourceNotFoundResponse (HttpRequest request, String resourceName) {

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
        response.setBody("Resource \"" + resourceName + "\" not found !!");
        return response;
    }

    /**
     * Handles when a file is not found
     * @param request Http Request
     * @param file File that was not found
     * @return HttpResponse the response
     */
    protected HttpResponse handleFileNotFoundResponse (HttpRequest request, File file) {

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
        response.setBody("File \"" + file.getAbsolutePath() + "\" not found !!");
        return response;
    }

    /**
     * Handles a response of file content
     * @param request Http Request
     * @param file The file to show
     * @return HttpResponse the response
     */
    protected HttpResponse handleFileResponse (HttpRequest request, File file) {

        byte[] fileBytes = null;
        try {
            fileBytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception ex) {
            throw new RuntimeException("Error reading file \"" + file + "\" !!");
        }

        Date lastModifiedDate = new Date(file.lastModified());
        String mimeType = MimeUtils.getMimeType(file);
        return handleFileResponse(request, fileBytes, mimeType, lastModifiedDate);
    }

    /**
     * Handles a response of file content
     * @param request Http Request
     * @param resourceBytes bytes of the resource
     * @param mimeType Mime type of the resource
     * @param lastModifiedDate last date the resource was modified
     * @return HttpResponse the response
     */
    protected HttpResponse handleFileResponse(HttpRequest request, byte[] resourceBytes, String mimeType, Date lastModifiedDate) {

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

        HttpResponse response = new HttpResponse();
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
                        try (ByteArrayOutputStream bout = new ByteArrayOutputStream(); GZIPOutputStream gzipper = new GZIPOutputStream(bout))
                        {
                            gzipper.write(resourceBytes, 0, resourceBytes.length);
                            gzipper.close();
                            resourceBytes = bout.toByteArray();
                        }
                    }
                    catch (IOException ex) {
                        throw new RuntimeException("Error compressing file !!", ex);
                    }
                }
            }
            response.setBody(resourceBytes);
        }

        return response;
    }
}
