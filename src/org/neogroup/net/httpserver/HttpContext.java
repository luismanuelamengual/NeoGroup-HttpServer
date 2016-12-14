
package org.neogroup.net.httpserver;

public abstract class HttpContext {

    private final String path;

    public HttpContext(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public abstract void onContext (HttpRequest request, HttpResponse response);
}
