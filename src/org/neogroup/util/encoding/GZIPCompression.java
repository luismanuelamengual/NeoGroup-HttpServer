package org.neogroup.util.encoding;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZIPCompression {

    public static byte[] compress(final byte[] bytes) throws IOException {

        try (ByteArrayOutputStream bout = new ByteArrayOutputStream(); GZIPOutputStream gzipper = new GZIPOutputStream(bout))
        {
            gzipper.write(bytes, 0, bytes.length);
            gzipper.close();
            return bout.toByteArray();
        }
    }

    public static byte[] decompress(final byte[] compressed) throws IOException {

        try (ByteArrayInputStream bin = new ByteArrayInputStream(compressed); GZIPInputStream gzipper = new GZIPInputStream(bin))
        {
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int len;
            while ((len = gzipper.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            gzipper.close();
            out.close();
            return out.toByteArray();
        }
    }
}