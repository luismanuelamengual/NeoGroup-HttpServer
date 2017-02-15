
package org.neogroup.httpserver.contexts;

import org.neogroup.httpserver.*;
import org.neogroup.util.MimeTypes;
import org.neogroup.util.encoding.GZIPCompression;

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

public class HttpFolderContext extends HttpContext {

    private static final String DEFAULT_DIGEST_ENCRYPTION = "MD5";
    private static final String URI_FOLDER_SEPARATOR = "/";
    private static final String CLASS_PATH_PREFIX = "${classpath}";
    private static final String FOLDER_HTML_DOCUMENT_TEMPLATE = "<!DOCTYPE html><html><head><title>%s</title><body>%s</body></html></head>";
    private static final String FOLDER_HTML_LIST_TEMPLATE = "<ul style=\"list-style-type: none;\">%s</ul>";
    private static final String FOLDER_HTML_ITEM_TEMPLATE = "<li><a href=\"%s\">%s</a></li>";

    protected final String folder;
    protected final boolean isClasspathFolder;

    public HttpFolderContext(String path, String folder) {
        super(path);
        this.isClasspathFolder = folder.startsWith(CLASS_PATH_PREFIX);
        if (this.isClasspathFolder) {
            folder = folder.substring(CLASS_PATH_PREFIX.length() + 1);
        }
        this.folder = folder;
    }

    @Override
    public HttpResponse onContext(HttpRequest request) {

        String path = request.getPath().substring(getPath().length());
        String fileName = folder + path.replaceAll(URI_FOLDER_SEPARATOR, File.separator);

        HttpResponse response;
        if (isClasspathFolder) {
            byte[] resourceBytes = getResourceBytes(fileName);
            if (resourceBytes != null) {
                response = handleFileResponse(request, resourceBytes, MimeTypes.getMimeType(fileName), null);
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
        response.addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_HTML);
        response.setBody(document.getBytes());
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

    protected HttpResponse handleResourceNotFoundResponse (HttpRequest request, String resourceName) {

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
        response.setBody("Resource \"" + resourceName + "\" not found !!");
        return response;
    }

    protected HttpResponse handleFileNotFoundResponse (HttpRequest request, File file) {

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpResponseCode.HTTP_NOT_FOUND);
        response.setBody("File \"" + file.getAbsolutePath() + "\" not found !!");
        return response;
    }

    protected HttpResponse handleFileResponse (HttpRequest request, File file) {

        byte[] fileBytes = null;
        try {
            fileBytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception ex) {
            throw new RuntimeException("Error reading file \"" + file + "\" !!");
        }

        Date lastModifiedDate = new Date(file.lastModified());
        String mimeType = MimeTypes.getMimeType(file);
        return handleFileResponse(request, fileBytes, mimeType, lastModifiedDate);
    }

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
                        resourceBytes = GZIPCompression.compress(resourceBytes);
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
