
package org.neogroup.net.httpserver;

import javax.net.ssl.SSLSession;

/**
 * This class encapsulates a HTTPS request received and a
 * response to be generated in one exchange and defines
 * the extensions to HttpExchange that are specific to the HTTPS protocol.
 * @since 1.6
 */

@jdk.Exported
public abstract class HttpsExchange extends HttpExchange {

    protected HttpsExchange () {
    }

    /**
     * Get the SSLSession for this exchange.
     * @return the SSLSession
     */
    public abstract SSLSession getSSLSession ();
}
