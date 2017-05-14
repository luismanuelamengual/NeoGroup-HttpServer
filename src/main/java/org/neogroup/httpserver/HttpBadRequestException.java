
package org.neogroup.httpserver;

/**
 * Bad Request exception
 */
public class HttpBadRequestException extends HttpException {

    public HttpBadRequestException(String msg) {
        super(msg);
    }

    public HttpBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
