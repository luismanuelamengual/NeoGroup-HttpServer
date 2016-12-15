
package org.neogroup.net.httpserver;

import java.util.HashMap;
import java.util.Map;

public class HttpHeaders {

    private final Map<String, String> headers;

    public HttpHeaders () {
        headers = new HashMap<>();
    }

    public void put (String name, String value) {
        headers.put(name, value);
    }

    public String get (String name) {
        return headers.get(name);
    }
}