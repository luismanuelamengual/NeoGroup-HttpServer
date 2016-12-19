package org.neogroup.net.httpserver;

public class HttpError extends RuntimeException {

    public HttpError (String msg) {
        super (msg);
    }

    public HttpError(String message, Throwable cause) {
        super(message, cause);
    }
}
