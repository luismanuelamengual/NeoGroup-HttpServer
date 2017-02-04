
package org.neogroup.httpserver;

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
