package me.bokan.perocasino.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataStoreTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @TempDir
    Path temp;

    private PlayerDataStore store() {
        return new PlayerDataStore(temp, Logger.getLogger("test"));
    }

    @Test
    void roundTrip() {
        PlayerDataStore store = store();
        PlayerData data = new PlayerData(ID);
        data.setWalletBalance(40);
        data.setDebt(7);
        data.setLoanDeadlineMillis(111L);
        data.setNextInterestMillis(222L);
        assertTrue(store.save(data, false));

        PlayerDataStore.Result loaded = store.load(ID);
        assertEquals(PlayerDataStore.Outcome.OK, loaded.outcome());
        assertEquals(40, loaded.data().getWalletBalance());
        assertEquals(7, loaded.data().getDebt());
        assertEquals(111L, loaded.data().getLoanDeadlineMillis());
        assertEquals(222L, loaded.data().getNextInterestMillis());
    }

    @Test
    void missingIsNotEmptyLibrary() {
        PlayerDataStore.Result loaded = store().load(ID);
        assertEquals(PlayerDataStore.Outcome.MISSING, loaded.outcome());
        assertNull(loaded.data());
    }

    @Test
    void corruptAndEmptyExistingAreFailed() throws Exception {
        PlayerDataStore store = store();
        Path file = store.fileFor(ID);
        Files.writeString(file, "not: yaml: broken\n");
        PlayerDataStore.Result corrupt = store.load(ID);
        assertEquals(PlayerDataStore.Outcome.FAILED, corrupt.outcome());

        Files.writeString(file, "");
        PlayerDataStore.Result empty = store.load(ID);
        assertEquals(PlayerDataStore.Outcome.FAILED, empty.outcome());
        assertEquals("empty file", empty.reason());
    }

    @Test
    void skipZeroStateWhenNoFile() {
        PlayerDataStore store = store();
        PlayerData data = new PlayerData(ID);
        assertTrue(store.save(data, false));
        assertFalse(Files.exists(store.fileFor(ID)));
    }

    @Test
    void parseRejectsMissingWallet() {
        try {
            PlayerDataYaml.parse(ID, "debt: 1\n");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("wallet"));
            return;
        }
        throw new AssertionError("expected parse failure");
    }
}
