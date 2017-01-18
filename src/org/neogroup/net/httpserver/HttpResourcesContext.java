
package org.neogroup.net.httpserver;

import org.neogroup.util.MimeTypes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class HttpResourcesContext extends HttpAbstractResourcesContext {

    public HttpResourcesContext(String path, String resourcesPath) {
        super(path, resourcesPath);
    }

    protected void onResourceRequest(HttpRequest request, HttpResponse response, String resourceName) {
        byte[] resourceBytes = getResourceBytes(resourceName);
        if (resourceBytes != null) {
            handleResourceResponse(request, response, resourceBytes, MimeTypes.getMimeType(resourceName), null);
        } else {
            handleResourceNotFoundResponse(request, response, resourceName);
        }
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
}
