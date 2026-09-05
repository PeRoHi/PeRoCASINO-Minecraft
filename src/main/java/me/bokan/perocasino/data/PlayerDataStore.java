package me.bokan.perocasino.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * プレイヤー経済 YAML の読込／原子書き込み。壊れたファイルは空データとして開かない。
 */
public final class PlayerDataStore {

    public enum Outcome {
        OK,
        MISSING,
        FAILED
    }

    public record Result(Outcome outcome, PlayerData data, String reason) {
        public static Result ok(PlayerData data) {
            return new Result(Outcome.OK, data, null);
        }

        public static Result missing() {
            return new Result(Outcome.MISSING, null, null);
        }

        public static Result failed(String reason) {
            return new Result(Outcome.FAILED, null, reason);
        }
    }

    private final Path directory;
    private final Logger logger;

    public PlayerDataStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public Path fileFor(UUID playerId) {
        return directory.resolve(playerId.toString().toLowerCase(Locale.ROOT) + ".yml");
    }

    public List<UUID> listPlayerIds() {
        List<UUID> ids = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.yml")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (name.endsWith(".tmp") || name.endsWith(".yml.tmp")) {
                    continue;
                }
                String stem = name.substring(0, name.length() - 4);
                try {
                    ids.add(UUID.fromString(stem));
                } catch (IllegalArgumentException ignored) {
                    warn("スキップ（UUIDでないファイル名）: " + name);
                }
            }
        } catch (IOException ex) {
            warn("players ディレクトリを列挙できません: " + ex.getMessage());
        }
        return ids;
    }

    public Result load(UUID playerId) {
        Path file = fileFor(playerId);
        if (!Files.exists(file)) {
            return Result.missing();
        }
        try {
            AtomicFiles.refuseSymlink(file, "leaf");
            Path parent = file.getParent();
            if (parent != null) {
                AtomicFiles.refuseSymlink(parent, "parent");
            }
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return Result.failed("empty file");
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            return Result.ok(PlayerDataYaml.parse(playerId, text));
        } catch (IllegalArgumentException | IOException ex) {
            return Result.failed(ex.getMessage());
        }
    }

    /**
     * @return 書き込んだ、または意図的にスキップした（失敗時は false）
     */
    public boolean save(PlayerData data, boolean fileAlreadyExists) {
        Path file = fileFor(data.getPlayerId());
        if (PlayerDataYaml.isZeroState(data) && !fileAlreadyExists && !Files.exists(file)) {
            return true;
        }
        try {
            byte[] body = PlayerDataYaml.serialize(data).getBytes(StandardCharsets.UTF_8);
            if (body.length == 0) {
                warn("空シリアライズを拒否 uuid=" + data.getPlayerId());
                return false;
            }
            AtomicFiles.writeAtomic(file, body);
            return true;
        } catch (IOException ex) {
            warn("保存失敗 uuid=" + data.getPlayerId() + " : " + ex.getMessage());
            return false;
        }
    }

    public boolean fileExists(UUID playerId) {
        return Files.exists(fileFor(playerId));
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
