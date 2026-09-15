package me.bokan.perocasino.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
            throw new IOException("target has no parent: " + target);
        }
        refuseSymlink(parent, "parent");
        if (existsNoFollow(target) && Files.isSymbolicLink(target)) {
            throw new IOException("refuse symlink leaf: " + target);
        }
        if (body.length == 0 && Files.exists(target) && Files.size(target) > 0) {
            throw new IOException("refuse empty overwrite of non-empty: " + target);
        }
        Files.createDirectories(parent);
        refuseSymlink(parent, "parent");
        Path tmp = parent.resolve(target.getFileName().toString() + ".tmp");
        prepareExclusiveTmp(tmp);
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
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
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void refuseSymlink(Path path, String label) throws IOException {
        if (existsNoFollow(path) && Files.isSymbolicLink(path)) {
            throw new IOException("refuse symlink " + label + ": " + path);
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
            throw new IOException("refuse symlink tmp: " + tmp);
        }
        if (!Files.isRegularFile(tmp, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("refuse non-regular tmp: " + tmp);
        }
        Files.delete(tmp);
    }
}
