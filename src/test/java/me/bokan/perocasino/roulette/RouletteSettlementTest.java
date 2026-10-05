package me.bokan.perocasino.roulette;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouletteSettlementTest {

    @Test
    void unpaidPayoutIsNotClearedOnLaterLoss() {
        assertEquals(RouletteSettlement.ChipFate.KEEP_NEW_PAYOUT,
                RouletteSettlement.decideChipFate(0, 40, false));
        assertEquals(RouletteSettlement.ChipFate.RETRY_UNPAID,
                RouletteSettlement.decideChipFate(40, 0, false));
        assertEquals(RouletteSettlement.ChipFate.CLEAR_AFTER_UNPAID,
                RouletteSettlement.decideChipFate(40, 0, true));
        assertEquals(RouletteSettlement.ChipFate.CLEAR,
                RouletteSettlement.decideChipFate(0, 0, true));
        assertEquals(RouletteSettlement.ChipFate.CLEAR,
                RouletteSettlement.decideChipFate(0, 40, true));
    }

    @Test
    void unpaidOpenCreditsThenClearsOrRefuses() {
        assertEquals(RouletteSettlement.UnpaidOpenFate.OPEN,
                RouletteSettlement.decideUnpaidOpen(0, true));
        assertEquals(RouletteSettlement.UnpaidOpenFate.OPEN,
                RouletteSettlement.decideUnpaidOpen(0, false));
        assertEquals(RouletteSettlement.UnpaidOpenFate.CLEAR_AFTER_CREDIT,
                RouletteSettlement.decideUnpaidOpen(40, true));
        assertEquals(RouletteSettlement.UnpaidOpenFate.REFUSE,
                RouletteSettlement.decideUnpaidOpen(40, false));
    }

    @Test
    void computePayoutUsesExistingMultipliersOnly() {
        assertEquals(0, RouletteSettlement.computePayout(5, 1, 8, 2));
        assertEquals(10, RouletteSettlement.computePayout(5, 2, 8, 2));
        assertEquals(40, RouletteSettlement.computePayout(5, 3, 8, 2));
        assertEquals(Integer.MAX_VALUE, RouletteSettlement.computePayout(Integer.MAX_VALUE, 3, 8, 2));
    }

    @Test
    void closedBoardContentsCountDiamondsAndMatches() {
        ItemStack[] contents = new ItemStack[54];
        contents[11] = new ItemStack(Material.DIAMOND, 3);
        contents[12] = new ItemStack(Material.DIAMOND_ORE, 1);
        contents[13] = new ItemStack(Material.GOLD_ORE, 1);
        contents[20] = new ItemStack(Material.COAL_ORE, 1);

        assertEquals(3, RouletteSettlement.countBetDiamonds(contents));
        RouletteSettlement.RoundResult hit = new RouletteSettlement.RoundResult(
                Material.DIAMOND_ORE, Material.IRON_ORE, Material.GOLD_ORE);
        assertEquals(2, RouletteSettlement.countOreMatches(contents, hit));
        RouletteSettlement.RoundResult miss = new RouletteSettlement.RoundResult(
                Material.LAPIS_ORE, Material.EMERALD_ORE, Material.REDSTONE_ORE);
        assertEquals(0, RouletteSettlement.countOreMatches(contents, miss));
    }
}
