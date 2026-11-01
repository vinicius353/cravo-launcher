package org.levimc.launcher.util;

import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class NativeLibraryPublisherTest {
    @Test public void failedPreparationPreservesExistingCacheAndArchive() throws Exception {
        Path root = Files.createTempDirectory("cravo-native-test");
        File output = root.resolve("libc++_shared.so").toFile();
        byte[] old = {1,2,3};
        Files.write(output.toPath(), old);
        try {
            NativeLibraryPublisher.publish(new ByteArrayInputStream(new byte[]{4,5,6}), output, file -> false);
            fail("Must propagate preparation failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("preparation failed"));
        }
        assertArrayEquals(old, Files.readAllBytes(output.toPath()));
        assertEquals(1, root.toFile().listFiles().length);
        output.delete(); root.toFile().delete();
    }
    @Test public void publishesFromApkWithoutModifyingOriginal() throws Exception {
        Path root = Files.createTempDirectory("cravo-zip-test");
        Path apk = root.resolve("base.apk.levi");
        byte[] data = {127,69,76,70,1,2,3};
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apk))) {
            zip.putNextEntry(new ZipEntry("lib/arm64-v8a/libc++_shared.so"));
            zip.write(data); zip.closeEntry();
        }
        byte[] original = Files.readAllBytes(apk);
        File output = root.resolve("libc++_shared.so").toFile();
        try (ZipFile zip = new ZipFile(apk.toFile()); InputStream in = zip.getInputStream(zip.getEntry("lib/arm64-v8a/libc++_shared.so"))) {
            NativeLibraryPublisher.publish(in, output, file -> file.length() == data.length);
        }
        assertArrayEquals(data, Files.readAllBytes(output.toPath()));
        assertArrayEquals(original, Files.readAllBytes(apk));
        output.delete(); apk.toFile().delete(); root.toFile().delete();
    }
    @Test public void emptyInputIsNotPublished() throws Exception {
        Path root = Files.createTempDirectory("cravo-empty-test");
        File output = root.resolve("libc++_shared.so").toFile();
        try {
            NativeLibraryPublisher.publish(new ByteArrayInputStream(new byte[0]), output, file -> true);
            fail("Empty input must fail");
        } catch (IOException expected) { assertFalse(output.exists()); }
        assertEquals(0, root.toFile().listFiles().length);
        root.toFile().delete();
    }
}
