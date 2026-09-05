package me.bokan.perocasino.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilesTest {

    @TempDir
    Path temp;

    @Test
    void writeThenReplace() throws IOException {
        Path file = temp.resolve("save.yml");
        AtomicFiles.writeAtomic(file, "hello: 1\n".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.writeAtomic(file, "hello: 2\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("hello: 2\n", Files.readString(file));
    }

    @Test
    void refuseEmptyOverwriteOfNonEmpty() throws IOException {
        Path file = temp.resolve("save.yml");
        AtomicFiles.writeAtomic(file, "wallet: 9\n".getBytes(StandardCharsets.UTF_8));
        IOException ex = assertThrows(IOException.class,
                () -> AtomicFiles.writeAtomic(file, new byte[0]));
        assertTrue(ex.getMessage().contains("empty overwrite"));
        assertEquals("wallet: 9\n", Files.readString(file));
    }

    @Test
    void refuseSymlinkLeaf() throws IOException {
        Path real = temp.resolve("real.yml");
        Files.writeString(real, "wallet: 1\n");
        Path link = temp.resolve("link.yml");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException skipped) {
            return;
        }
        IOException ex = assertThrows(IOException.class,
                () -> AtomicFiles.writeAtomic(link, "wallet: 2\n".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ex.getMessage().contains("symlink"));
        assertEquals("wallet: 1\n", Files.readString(real));
    }
}
