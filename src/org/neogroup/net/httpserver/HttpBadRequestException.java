
package org.neogroup.net.httpserver;

public class HttpBadRequestException extends HttpException {

    public HttpBadRequestException(String msg) {
        super(msg);
    }

    public HttpBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
