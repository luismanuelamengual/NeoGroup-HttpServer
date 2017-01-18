package org.neogroup.net.httpserver;

import org.neogroup.util.MimeTypes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;

public class HttpFolderContext extends HttpAbstractResourcesContext {

    private static final String FOLDER_HTML_DOCUMENT_TEMPLATE = "<!DOCTYPE html><html><head><title>%s</title><body>%s</body></html></head>";
    private static final String FOLDER_HTML_LIST_TEMPLATE = "<ul style=\"list-style-type: none;\">%s</ul>";
    private static final String FOLDER_HTML_ITEM_TEMPLATE = "<li><a href=\"%s\">%s</a></li>";

    public HttpFolderContext(String path, String folderPath) {
        super(path, folderPath);
    }

    @Override
    protected void onResourceRequest(HttpRequest request, HttpResponse response, String resourceName) {
        File file = new File(resourceName);
        if(file.exists()) {
            if (file.isDirectory()) {
                handleDirectoryResponse (request, response, file);
            }
            else {
                handleFileResponse (request, response, file);
            }
        }
        else {
            handleResourceNotFoundResponse (request, response, file.getName());
        }
    }

    protected void handleDirectoryResponse (HttpRequest request, HttpResponse response, File file) {

        StringBuilder list = new StringBuilder();
        Path filePath = file.toPath();
        Path baseFilePath = Paths.get(resourcesPath);
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
        response.addHeader(HttpHeader.CONTENT_TYPE, MimeTypes.TEXT_HTML);
        response.setBody(document.getBytes());
    }

    protected void handleFileResponse (HttpRequest request, HttpResponse response, File file) {

        byte[] fileBytes = null;
        try {
            fileBytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception ex) {
            throw new RuntimeException("Error reading file \"" + file + "\" !!");
        }

        Date lastModifiedDate = new Date(file.lastModified());
        String mimeType = MimeTypes.getMimeType(file);
        handleResourceResponse(request, response, fileBytes, mimeType, lastModifiedDate);
    }
}
