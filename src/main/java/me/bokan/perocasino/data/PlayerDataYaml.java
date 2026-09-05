package me.bokan.perocasino.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤー経済ファイルの最小 YAML（既知キーのみ）。壊れていたら例外。
 */
public final class PlayerDataYaml {

    private PlayerDataYaml() {}

    public static String serialize(PlayerData data) {
        synchronized (data) {
            return "wallet: " + data.getWalletBalance() + "\n"
                    + "debt: " + data.getDebt() + "\n"
                    + "loan-deadline-millis: " + data.getLoanDeadlineMillis() + "\n"
                    + "next-interest-millis: " + data.getNextInterestMillis() + "\n"
                    + "last-roulette-index: " + data.getLastRouletteIndex() + "\n";
        }
    }

    public static PlayerData parse(UUID playerId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty player data");
        }
        Map<String, String> values = new LinkedHashMap<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("invalid yaml line " + (i + 1));
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (key.isEmpty() || values.containsKey(key)) {
                throw new IllegalArgumentException("duplicate or empty key: " + key);
            }
            values.put(key, value);
        }
        if (!values.containsKey("wallet") || !values.containsKey("debt")) {
            throw new IllegalArgumentException("missing required keys wallet/debt");
        }
        PlayerData data = new PlayerData(playerId);
        data.setWalletBalance(requireNonNegativeInt(values.get("wallet"), "wallet"));
        data.setDebt(requireNonNegativeInt(values.get("debt"), "debt"));
        if (values.containsKey("loan-deadline-millis")) {
            data.setLoanDeadlineMillis(requireLong(values.get("loan-deadline-millis"), "loan-deadline-millis"));
        }
        if (values.containsKey("next-interest-millis")) {
            data.setNextInterestMillis(requireLong(values.get("next-interest-millis"), "next-interest-millis"));
        }
        if (values.containsKey("last-roulette-index")) {
            data.setLastRouletteIndex(requireInt(values.get("last-roulette-index"), "last-roulette-index"));
        }
        return data;
    }

    public static boolean isZeroState(PlayerData data) {
        return data.getWalletBalance() == 0
                && data.getDebt() == 0
                && data.getLoanDeadlineMillis() == 0L
                && data.getNextInterestMillis() == 0L
                && data.getLastRouletteIndex() == 0;
    }

    private static int requireNonNegativeInt(String raw, String key) {
        int n = requireInt(raw, key);
        if (n < 0) {
            throw new IllegalArgumentException(key + " is negative");
        }
        return n;
    }

    private static int requireInt(String raw, String key) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid int " + key + "=" + raw);
        }
    }

    private static long requireLong(String raw, String key) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid long " + key + "=" + raw);
        }
    }
}
