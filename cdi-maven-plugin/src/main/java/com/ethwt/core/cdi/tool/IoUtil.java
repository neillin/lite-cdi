package com.ethwt.core.cdi.tool;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

public class IoUtil {
    public static InputStream readClass(ClassLoader classLoader, String className) {
        return classLoader.getResourceAsStream(className.replace('.', '/') + ".class");
    }

    public static byte[] readClassAsBytes(ClassLoader classLoader, String className) throws IOException {
        try (InputStream stream = readClass(classLoader, className)) {
            return readBytes(stream);
        }
    }

    public static byte[] readBytes(InputStream is) throws IOException {
        return IOUtils.toByteArray(is);
    }
}
