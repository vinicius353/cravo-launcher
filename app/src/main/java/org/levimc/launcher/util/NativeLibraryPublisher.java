package org.levimc.launcher.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Prepare a private copy before publishing it; never mutate the archive or a valid cache entry. */
public final class NativeLibraryPublisher {
    private NativeLibraryPublisher() {}
    public interface Preparer { boolean prepare(File file); }

    public static void publish(InputStream input, File output, Preparer preparer) throws IOException {
        File parent = output.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new IOException("Cannot create native cache: " + parent);
        File stage = File.createTempFile(".prepare-", ".so", parent);
        try {
            try (FileOutputStream stream = new FileOutputStream(stage)) {
                byte[] buffer = new byte[131072];
                int count;
                while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                stream.getFD().sync();
            }
            if (stage.length() == 0) throw new IOException("Empty native library: " + output);
            // Preparation may rewrite an ELF; do not make it read-only beforehand.
            if (!preparer.prepare(stage))
                throw new IOException("Native image preparation failed for " + output.getName());
            if (!stage.isFile() || stage.length() == 0)
                throw new IOException("Native image preparation produced no output: " + output);
            if (!stage.setReadOnly()) throw new IOException("Cannot protect native library: " + output);
            Files.move(stage.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(stage.toPath());
            // NativeImageGuard's marker belongs to the temporary pathname, not the published file.
            File[] markers = parent.listFiles((dir, name) -> name.startsWith("." + stage.getName() + "."));
            if (markers != null) for (File marker : markers) marker.delete();
        }
    }
}
