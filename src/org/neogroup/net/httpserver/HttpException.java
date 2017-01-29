
package org.neogroup.net.httpserver;

public class HttpException extends RuntimeException {

    public HttpException(String msg) {
        super (msg);
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
