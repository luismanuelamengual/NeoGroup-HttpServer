
package org.neogroup.httpserver.contexts;

import org.neogroup.httpserver.HttpRequest;
import org.neogroup.httpserver.HttpResponse;

/**
 * Http Context
 */
public abstract class HttpContext {

    private final String path;

    /**
     * Constructor with the pat
     * @param path path to access the context
     */
    public HttpContext(String path) {
        this.path = path;
    }

    /**
     * Retrieve the path to access the context
     * @return String path
     */
    public String getPath() {
        return path;
    }

    /**
     * Method that is execute when accesing the context path
     * @param request Http request
     * @return http response
     */
    public abstract HttpResponse onContext (HttpRequest request);
}
