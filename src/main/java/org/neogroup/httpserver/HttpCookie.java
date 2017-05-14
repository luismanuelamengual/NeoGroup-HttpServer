
package org.neogroup.httpserver;

import java.util.Date;

/**
 * Class that holds information of a cookie
 */
public class HttpCookie {

    private String name;
    private String value;
    private Date expires;
    private Integer maxAge;
    private String domain;
    private String path;
    private Boolean secure;
    private Boolean httpOnly;

    /**
     * Default constructor
     */
    public HttpCookie() {
    }

    /**
     * Constructor with name and value of a cookie
     * @param name Name of the cookie
     * @param value Value of the cookie
     */
    public HttpCookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Retrieves the name of the cookie
     * @return String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the cookie
     * @param name Name of the cookie
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the value of the cookie
     * @return String
     */
    public String getValue() {
        return value;
    }

    /**
     * Set the value of the cookie
     * @param value Value of the cookie
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Retrieves the cookies expiration date
     * @return Date expiratiion date of the cookie
     */
    public Date getExpires() {
        return expires;
    }

    /**
     * Set the cookies expiration date
     * @param expires Expiration date of the cookie
     */
    public void setExpires(Date expires) {
        this.expires = expires;
    }

    /**
     * Retrieves the cookies max age
     * @return Max age of the cookie
     */
    public Integer getMaxAge() {
        return maxAge;
    }

    /**
     * Set the cookie max age
     * @param maxAge Max age of the cookie
     */
    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    /**
     * Retrieves the domain associated with the cookie
     * @return domain of the cookie
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Sets the domain associated with the cookie
     * @param domain domain of the cookie
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * Retrieves the path associated with the cookie
     * @return path of the cookie
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the path associated with the cookie
     * @param path path of the cookie
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Indicates if the cookie is secure or not
     * @return boolean
     */
    public Boolean getSecure() {
        return secure;
    }

    /**
     * Sets if the cookie is secure
     * @param secure secure cookie
     */
    public void setSecure(Boolean secure) {
        this.secure = secure;
    }

    /**
     * Indicates if the cookie is only for http
     * @return boolean
     */
    public Boolean getHttpOnly() {
        return httpOnly;
    }

    /**
     * Sets if the cookie is only for http
     * @param httpOnly http only cookie
     */
    public void setHttpOnly(Boolean httpOnly) {
        this.httpOnly = httpOnly;
    }
}
