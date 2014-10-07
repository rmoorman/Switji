package org.jinglenodes.util;

import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author bhlangonijr
 *         Date: 10/6/14
 *         Time: 4:09 PM
 */
public class ZipUtil {

    final private static Logger log = Logger.getLogger(ZipUtil.class);
    final private static String ENCODE = "UTF-8";

    public static byte[] zip(final byte[] input) throws UnsupportedEncodingException, IOException {
        Deflater df = new Deflater();
        df.setInput(input);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);
        df.finish();
        byte[] buff = new byte[1024];
        while (!df.finished()) {
            int count = df.deflate(buff);
            baos.write(buff, 0, count);
        }
        baos.close();
        byte[] output = baos.toByteArray();

        log.debug("Original: " + input.length + "b - Compressed: " + output.length + "b");
        return output;
    }

    public static byte[] zip(final String data) throws UnsupportedEncodingException, IOException {
        byte[] input = data.getBytes(ENCODE);
        return zip(input);
    }

    public static String unzip(final String data) throws IOException, DataFormatException {
        return unzip(data.getBytes(ENCODE));
    }

    public static String unzip(byte[] input) throws IOException, DataFormatException {

        return new String(unzipBytes(input));
    }

    public static byte[] unzipBytes(byte[] input) throws IOException, DataFormatException {
        Inflater ifl = new Inflater();
        ifl.setInput(input);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);
        byte[] buff = new byte[1024];
        while (!ifl.finished()) {
            int count = ifl.inflate(buff);
            baos.write(buff, 0, count);
        }
        baos.close();
        byte[] output = baos.toByteArray();

        return output;
    }

}
