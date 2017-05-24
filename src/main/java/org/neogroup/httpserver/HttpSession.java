
package org.neogroup.httpserver;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Session that holds information for an http client
 */
public class HttpSession {

    private final UUID id;
    private final Map<String,Object> attributes;

    /**
     * Constructor for the http session
     */
    public HttpSession() {
        this.id = UUID.randomUUID();
        this.attributes = new HashMap<>();
    }

    /**
     * Get thte id of the session
     * @return id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Set an attribute value
     * @param name name of attribute
     * @param value value of attribute
     */
    public void setAttribute (String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * Get an attribute value
     * @param name name of attribute
     * @param <R> type of response
     * @return casted value
     */
    public <R> R getAttribute (String name) {
        return (R)attributes.get(name);
    }

    /**
     * Indicates if an attribute exists
     * @param name name of attribute
     * @return boolean
     */
    public boolean hasAttribute (String name) {
        return attributes.containsKey(name);
    }

    /**
     * Removes all attributes from the session
     */
    public void clearAttributes () {
        attributes.clear();
    }

    /**
     * Ge attribute names
     * @return set of attribute names
     */
    public Set<String> getAttributeNames () {
        return attributes.keySet();
    }
}
