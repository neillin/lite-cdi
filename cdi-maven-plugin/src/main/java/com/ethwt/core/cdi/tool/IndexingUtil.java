package com.ethwt.core.cdi.tool;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.UnsupportedVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IndexingUtil {

    private static final Logger log = LoggerFactory.getLogger(IndexingUtil.class);

    public static final DotName OBJECT = DotName.createSimple(Object.class.getName());

    public static final String JANDEX_INDEX = "META-INF/jandex.idx";
    public static final String BEANS_XML = "META-INF/beans.xml";
    public static final String META_INF_VERSIONS = "META-INF/versions/";

    // At least Jandex 2.1 is needed
    private static final int REQUIRED_INDEX_VERSION = 8;
    public static final int JAVA_VERSION;

    static {
        int version = 8;
        try {
            Method versionMethod = Runtime.class.getMethod("version");
            Object v = versionMethod.invoke(null);
            @SuppressWarnings("unchecked")
			List<Integer> list = (List<Integer>) v.getClass().getMethod("version").invoke(v);
            version = list.get(0);
        } catch (Exception e) {
            //version 8
        }
        JAVA_VERSION = version;
    }

    public static Index indexJar(Path path) throws IOException {
        return indexJar(path.toFile(), Collections.emptySet());
    }

    public static Index indexJar(File file) throws IOException {
        return indexJar(file, Collections.emptySet());
    }
    
    public static boolean isBeanArchive(File file) {
    	try (JarFile jarFile = new JarFile(file)) {
    		 ZipEntry existing = jarFile.getEntry(JANDEX_INDEX);
    		 if (existing != null) {
    			 return true;
    		 }
    		 existing = jarFile.getEntry(BEANS_XML);
    		 if (existing != null) {
    			 return true;
    		 }
    		 return false;
    	} catch(Exception e) {
    		log.error("Failed to read file content of: {}", file, e);
    		return false;
    	}
    }

    public static Index indexJar(Path path, Set<String> removed) throws IOException {
        return indexJar(path.toFile(), removed);
    }

    public static Index indexJar(File file, Set<String> removed) throws IOException {
        try (JarFile jarFile = new JarFile(file)) {
            ZipEntry existing = jarFile.getEntry(JANDEX_INDEX);
            if (existing != null && removed == null) {
                try (InputStream in = jarFile.getInputStream(existing)) {
                    IndexReader reader = new IndexReader(in);
                    if (reader.getIndexVersion() < REQUIRED_INDEX_VERSION) {
                        log.warn(
                                "Re-indexing {} - at least Jandex 2.1 must be used to index an application dependency",
                                file);
                        return indexJar(jarFile, removed);
                    } else {
                        try {
                            return reader.read();
                        } catch (UnsupportedVersion e) {
                            throw new UnsupportedVersion("Can't read Jandex index from " + file + ": " + e.getMessage());
                        }
                    }
                }
            }
            return indexJar(jarFile, removed);
        }
    }

    private static Index indexJar(JarFile file, Set<String> removed) throws IOException {
        Indexer indexer = new Indexer();
        Enumeration<JarEntry> e = file.entries();
        boolean multiRelease = JarFiles.isMultiRelease(file);
        while (e.hasMoreElements()) {
            JarEntry entry = e.nextElement();
            if (removed != null && removed.contains(entry.getName())) {
                continue;
            }
            if (entry.getName().endsWith(".class")) {
                if (multiRelease && entry.getName().startsWith(META_INF_VERSIONS)) {
                    String part = entry.getName().substring(META_INF_VERSIONS.length());
                    int slash = part.indexOf("/");
                    if (slash != -1) {
                        try {
                            int ver = Integer.parseInt(part.substring(0, slash));
                            if (ver <= JAVA_VERSION) {
                                try (InputStream inputStream = file.getInputStream(entry)) {
                                    indexer.index(inputStream);
                                }
                            }
                        } catch (NumberFormatException ex) {
                            log.debug("Failed to parse META-INF/versions entry", ex);
                        }
                    }
                } else {
                    try (InputStream inputStream = file.getInputStream(entry)) {
                        indexer.index(inputStream);
                    }
                }
            }
        }
        return indexer.complete();
    }

    public static void indexClass(String className, Indexer indexer, IndexView quarkusIndex,
            Set<DotName> additionalIndex, ClassLoader classLoader) {
        DotName classDotName = DotName.createSimple(className);
        if (additionalIndex.contains(classDotName)) {
            return;
        }
        ClassInfo classInfo = quarkusIndex.getClassByName(classDotName);
        if (classInfo == null) {
            log.debug("Index class: {}", className);
            try (InputStream stream = IoUtil.readClass(classLoader, className)) {
                classInfo = indexer.index(stream);
                additionalIndex.add(classInfo.name());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to index: " + className, e);
            }
        } else {
            // The class could be indexed by quarkus - we still need to distinguish framework classes
            additionalIndex.add(classDotName);
        }
        for (DotName annotationName : classInfo.annotations().keySet()) {
            if (!additionalIndex.contains(annotationName) && quarkusIndex.getClassByName(annotationName) == null) {
                try (InputStream annotationStream = IoUtil.readClass(classLoader, annotationName.toString())) {
                    if (annotationStream == null) {
                        log.debug("Could not index annotation: {} (missing class or dependency)", annotationName);
                    } else {
                        log.debug("Index annotation: {}", annotationName);
                        indexer.index(annotationStream);
                        additionalIndex.add(annotationName);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to index: " + className, e);
                }
            }
        }
        if (classInfo.superName() != null && !classInfo.superName().equals(OBJECT)) {
            indexClass(classInfo.superName().toString(), indexer, quarkusIndex, additionalIndex, classLoader);
        }
    }

    public static void indexClass(String className, Indexer indexer,
            IndexView quarkusIndex, Set<DotName> additionalIndex,
            ClassLoader classLoader, byte[] beanData) {
        DotName classDotName = DotName.createSimple(className);
        if (additionalIndex.contains(classDotName)) {
            return;
        }
        ClassInfo classInfo = quarkusIndex.getClassByName(classDotName);
        if (classInfo == null) {
            log.debug("Index class: {}", className);
            try (InputStream stream = new ByteArrayInputStream(beanData)) {
                classInfo = indexer.index(stream);
                additionalIndex.add(classInfo.name());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to index: " + className, e);
            }
        } else {
            // The class could be indexed by quarkus - we still need to distinguish framework classes
            additionalIndex.add(classDotName);
        }
        for (DotName annotationName : classInfo.annotations().keySet()) {
            if (!additionalIndex.contains(annotationName) && quarkusIndex.getClassByName(annotationName) == null) {
                try (InputStream annotationStream = IoUtil.readClass(classLoader, annotationName.toString())) {
                    log.debug("Index annotation: {}", annotationName);
                    indexer.index(annotationStream);
                    additionalIndex.add(annotationName);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to index: " + className, e);
                }
            }
        }
    }
}
