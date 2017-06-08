
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
    private boolean valid;
    private boolean isNew;
    private long lastActivityTimestamp;
    private long creationTimestamp;
    private int maxInactiveInterval;

    /**
     * Constructor for the http session
     */
    protected HttpSession() {
        this.id = UUID.randomUUID();
        this.attributes = new HashMap<>();
        long time = System.currentTimeMillis();
        creationTimestamp = time;
        lastActivityTimestamp = time;
        valid = false;
        isNew = true;
    }

    /**
     * Get thte id of the session
     * @return id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Get the session creation timestamp
     * @return long
     */
    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    /**
     * Get the session last activity timestamp
     * @return long
     */
    public long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }

    /**
     * Set the session last activity timestamp
     */
    protected void checkSession() {
        this.lastActivityTimestamp = System.currentTimeMillis();
        this.isNew = false;
    }

    /**
     * Get the maximum amount of milliseconds a session can be alive
     * @return int inactive milliseconds
     */
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    /**
     * Set the maximum amount of milliseconds a session can be alive
     * @param maxInactiveInterval inactive milliseconds
     */
    public void setMaxInactiveInterval(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
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

    /**
     * Indicates if the session was created or not
     * @return boolean
     */
    public boolean isNew() {
        return isNew;
    }

    /**
     * Checks if the session is still valid
     * @return boolean
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Invalidates the session so its not valid any more
     */
    public void invalidate () {
        valid = false;
        clearAttributes();
    }
}
