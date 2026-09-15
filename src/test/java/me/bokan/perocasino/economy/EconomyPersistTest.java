package me.bokan.perocasino.economy;

import me.bokan.perocasino.data.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyPersistTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @TempDir
    Path temp;

    @Test
    void loadThenSaveRoundTripThroughManager() {
        PlayerDataStore store = new PlayerDataStore(temp, Logger.getLogger("test"));
        EconomyManager first = new EconomyManager();
        first.attachStore(store);
        assertTrue(first.tryDepositWallet(ID, 15));
        assertTrue(first.tryBorrow(ID, 3));

        EconomyManager second = new EconomyManager();
        second.attachStore(store);
        second.loadAll();
        assertEquals(18, second.getWalletBalance(ID));
        assertEquals(3, second.getDebt(ID));
    }

    @Test
    void getDataLoadsDiskBeforeCreatingZeros() throws Exception {
        PlayerDataStore store = new PlayerDataStore(temp, Logger.getLogger("test"));
        Path file = store.fileFor(ID);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "wallet: 40\ndebt: 2\n");

        EconomyManager economy = new EconomyManager();
        economy.attachStore(store);
        // loadAll を飛ばしても既存ファイルをゼロで上書きしない
        assertEquals(40, economy.getWalletBalance(ID));
        assertTrue(economy.tryDepositWallet(ID, 1));
        assertEquals(41, economy.getWalletBalance(ID));
        assertTrue(Files.readString(file).contains("wallet: 41"));
    }

    @Test
    void creditPayoutKeepsFalseWhenOfflineAndWalletRejected() {
        EconomyManager economy = new EconomyManager();
        UUID id = ID;
        economy.getData(id).setWalletBalance(Integer.MAX_VALUE);
        assertFalse(economy.creditPayout(id, null, 1));
        assertEquals(Integer.MAX_VALUE, economy.getWalletBalance(id));
    }

    @Test
    void creditPayoutDepositsWhenWalletHasRoom() {
        EconomyManager economy = new EconomyManager();
        assertTrue(economy.creditPayout(ID, null, 4));
        assertEquals(4, economy.getWalletBalance(ID));
    }

    @Test
    void corruptFileRefusesMutationAndOverwrite() throws Exception {
        PlayerDataStore store = new PlayerDataStore(temp, Logger.getLogger("test"));
        Path file = store.fileFor(ID);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "wallet: nope\ndebt: 1\n");

        EconomyManager economy = new EconomyManager();
        economy.attachStore(store);
        economy.loadAll();
        assertTrue(economy.isLoadFailed(ID));
        assertFalse(economy.tryDepositWallet(ID, 10));
        assertEquals(0, economy.getWalletBalance(ID));
        assertEquals("wallet: nope\ndebt: 1\n", Files.readString(file));
    }
}
