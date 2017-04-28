
package org.neogroup.httpserver;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class HttpServerUtils {

    private static final String SERVER_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static DateFormat dateFormatter;

    static {

        dateFormatter = new SimpleDateFormat(SERVER_DATE_FORMAT);
        dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static final String formatDate (Date date) {
        return dateFormatter.format(date);
    }

    public static final Date getDate (String dateString) throws ParseException {
        return dateFormatter.parse(dateString);
    }
}