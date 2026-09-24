package me.bokan.perocasino.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * tmp へ書いて fsync したあと replace する。シンボリックリンクと空上書きは拒否する。
 */
public final class AtomicFiles {

    private AtomicFiles() {}

    public static void writeAtomic(Path target, byte[] body) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(body, "body");
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("target has no parent");
        }
        refuseSymlink(parent, "parent");
        if (existsNoFollow(target) && Files.isSymbolicLink(target)) {
            throw new IOException("refuse symlink leaf");
        }
        if (body.length == 0 && existsNoFollow(target)
                && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && Files.size(target) > 0) {
            throw new IOException("refuse empty overwrite of non-empty");
        }
        Files.createDirectories(parent);
        refuseSymlink(parent, "parent");
        Path tmp = parent.resolve(target.getFileName().toString() + ".tmp");
        prepareExclusiveTmp(tmp);
        try (FileChannel channel = FileChannel.open(tmp,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                StandardOpenOption.NOFOLLOW_LINKS)) {
            channel.write(ByteBuffer.wrap(body));
            channel.force(true);
        } catch (IOException ex) {
            try {
                if (existsNoFollow(tmp) && !Files.isSymbolicLink(tmp)) {
                    Files.deleteIfExists(tmp);
                }
            } catch (IOException ignored) {
                // 本体の失敗を優先
            }
            throw ex;
        }
        if (!Files.isRegularFile(tmp, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("refuse non-regular tmp before replace");
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        fsyncDest(target);
    }

    /**
     * 葉を follow せず通常ファイルだけ読む。symlink / 非通常 / 欠落は例外。
     */
    public static byte[] readNoFollow(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (existsNoFollow(path) && Files.isSymbolicLink(path)) {
            throw new IOException("refuse symlink leaf");
        }
        Path parent = path.getParent();
        if (parent != null) {
            refuseSymlink(parent, "parent");
        }
        if (!existsNoFollow(path)) {
            throw new NoSuchFileException(path.toString());
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("refuse non-regular leaf");
        }
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.READ,
                StandardOpenOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size > Integer.MAX_VALUE) {
                throw new IOException("file too large");
            }
            ByteBuffer buf = ByteBuffer.allocate((int) size);
            while (buf.hasRemaining()) {
                if (channel.read(buf) < 0) {
                    break;
                }
            }
            return java.util.Arrays.copyOf(buf.array(), buf.position());
        }
    }

    public static void refuseSymlink(Path path, String label) throws IOException {
        if (existsNoFollow(path) && Files.isSymbolicLink(path)) {
            throw new IOException("refuse symlink " + label);
        }
    }

    public static boolean existsNoFollow(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * 残存 tmp は通常ファイルなら消す。シンボリックリンクや非通常ファイルは拒否する。
     * 本体の open は CREATE_NEW（O_EXCL 相当）。
     */
    private static void prepareExclusiveTmp(Path tmp) throws IOException {
        if (!existsNoFollow(tmp)) {
            return;
        }
        if (Files.isSymbolicLink(tmp)) {
            throw new IOException("refuse symlink tmp");
        }
        if (!Files.isRegularFile(tmp, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("refuse non-regular tmp");
        }
        Files.delete(tmp);
    }

    private static void fsyncDest(Path target) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("refuse non-regular dest after replace");
        }
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.WRITE,
                StandardOpenOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }
}
