
package org.neogroup.httpserver.contexts;

import org.neogroup.httpserver.HttpRequest;
import org.neogroup.httpserver.HttpResponse;

public abstract class HttpContext {

    private final String path;

    public HttpContext(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public abstract HttpResponse onContext (HttpRequest request);
}
