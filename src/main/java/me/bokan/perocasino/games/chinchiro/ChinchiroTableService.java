package me.bokan.perocasino.games.chinchiro;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 村人ディーラー経由のチンチロ卓。親（ホスト）が1回振り、各子が順に振って親と比べ、財布ダイヤで精算する。
 */
public final class ChinchiroTableService implements Listener {

    private static final String PERM_PLAY = "perocasino.chinchiro.table";
    private static final String CONFIRM_TITLE = "§0§lCHINCHIRO: 開始確認";
    private static final String LOBBY_TITLE = "§0§lCHINCHIRO: ロビー";
    private static final String BET_TITLE = "§0§lCHINCHIRO: 掛け金";

    /**
     * 子リスト専用の仮UUID。ロビーの {@code bets} には含めない。1人のとき子として振る相手＝ハウス（ディーラー側）。
     */
    private static final UUID HOUSE_CHILD_PLACEHOLDER = UUID.fromString("c7770000-0000-4000-8000-000000000001");

    private static boolean isHouseChildPlaceholder(UUID id) {
        return id != null && HOUSE_CHILD_PLACEHOLDER.equals(id);
    }

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final ChinchiroDiceService dice;

    private int joinRadius = 16;
    private int maxPlayers = 8;
    private int rollDelayTicks = 40;

    private Table table;
    private long rollChainToken;
    private BukkitTask pendingRollTask;

    public ChinchiroTableService(JavaPlugin plugin, EconomyManager economy, ChinchiroDiceService dice) {
        this.plugin = plugin;
        this.economy = economy;
        this.dice = dice;
        reloadFromConfig();
        applyConfiguredDealerNpcSettings();
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        joinRadius = Math.max(4, cfg.getInt("chinchiro.table.join-radius", 16));
        maxPlayers = Math.clamp(cfg.getInt("chinchiro.table.max-players", 8), 2, 16);
        rollDelayTicks = Math.max(10, cfg.getInt("chinchiro.table.roll-delay-ticks", 40));
    }

    /** config の UUID に一致するディーラー村人がいれば AI/重力を無効化。 */
    public void applyConfiguredDealerNpcSettings() {
        String raw = plugin.getConfig().getString("chinchiro.dealer.uuid", "");
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            UUID id = UUID.fromString(raw.trim());
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Villager v) {
                configureDealerNpc(v);
            }
        } catch (Exception ignored) {
        }
    }

    public void shutdown() {
        cancelRollChain();
        if (table != null) {
            abortRoundAndResetLobby("§cチンチロ卓を終了しました。");
        }
    }

    private void cancelRollChain() {
        rollChainToken++;
        if (pendingRollTask != null) {
            pendingRollTask.cancel();
            pendingRollTask = null;
        }
    }

    public void openJoinConfirm(Player player) {
        Entity dealer = findDealerFor(player);
        if (dealer == null) {
            player.sendMessage("§cチンチロのディーラーが見つかりません。§7管理者: §f/perocasino chinchiro dealer set|summon");
            return;
        }
        if (!player.hasPermission(PERM_PLAY)) {
            player.sendMessage("§c権限がありません。");
            return;
        }
        openConfirm(player, dealer);
    }

    private void openConfirm(Player player, Entity dealer) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);
        inv.setItem(11, icon(Material.LIME_CONCRETE, "§a§l参加する", List.of(
                "§7親子形式のチンチロ卓に入ります。",
                "§7最初に参加した人が親（ホスト）です。",
                "§7※ 1人だけでも、ディーラー（ハウス）が子として対戦します。"
        )));
        inv.setItem(15, icon(Material.RED_CONCRETE, "§c§l参加しない", null));
        inv.setItem(22, icon(Material.BARRIER, "§7閉じる", null));
        player.openInventory(inv);
        player.setMetadata("perocasino_chinchiro_dealer", new FixedMetadataValue(plugin, dealer.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        if (!isDealer(villager)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (table != null && table.phase == Phase.ROLLING) {
            if (!table.bets.containsKey(player.getUniqueId())) {
                player.sendMessage("§cチンチロは進行中です。次のラウンドまでお待ちください。");
                return;
            }
        }
        if (resumeToLobby(player)) {
            return;
        }
        openConfirm(player, villager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (!isDealer(villager)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = event.getView().getTitle();

        if (CONFIRM_TITLE.equals(title)) {
            event.setCancelled(true);
            if (!isTopClick(event)) {
                return;
            }
            if (event.getSlot() == 11) {
                Entity dealer = resolveDealerFromMeta(player);
                if (dealer == null) {
                    dealer = findDealerFor(player);
                }
                if (dealer == null) {
                    player.sendMessage("§cチンチロのディーラーが見つかりません。");
                    player.closeInventory();
                    return;
                }
                joinLobby(player, dealer);
            } else if (event.getSlot() == 15 || event.getSlot() == 22) {
                player.closeInventory();
            }
            return;
        }

        if (LOBBY_TITLE.equals(title)) {
            event.setCancelled(true);
            if (!isTopClick(event)) {
                return;
            }
            UUID id = player.getUniqueId();
            if (table == null || !table.bets.containsKey(id)) {
                player.closeInventory();
                return;
            }
            switch (event.getSlot()) {
                case 11 -> openBet(player);
                case 13 -> {
                    if (!id.equals(table.host)) {
                        player.sendMessage("§e開始できるのは親（ホスト）だけです。");
                        return;
                    }
                    startRound(player);
                }
                case 15 -> leaveTable(player, true);
                default -> {
                }
            }
            return;
        }

        if (BET_TITLE.equals(title)) {
            event.setCancelled(true);
            if (!isTopClick(event)) {
                return;
            }
            if (table == null || table.phase != Phase.LOBBY || !table.bets.containsKey(player.getUniqueId())) {
                player.closeInventory();
                return;
            }
            int amount = switch (event.getSlot()) {
                case 10 -> 1;
                case 11 -> 2;
                case 12 -> 5;
                case 13 -> 10;
                case 14 -> 32;
                case 15 -> 64;
                default -> 0;
            };
            if (amount > 0) {
                setBet(player, amount);
            } else if (event.getSlot() == 22) {
                openLobby(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (CONFIRM_TITLE.equals(event.getView().getTitle())) {
            player.removeMetadata("perocasino_chinchiro_dealer", plugin);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leaveTable(event.getPlayer(), false);
    }

    private void joinLobby(Player player, Entity dealer) {
        if (table != null && table.phase == Phase.LOBBY && !dealer.getUniqueId().equals(table.dealerId)) {
            player.sendMessage("§c別のディーラーで開いているチンチロ卓があります。そちらの卓に参加するか、全員が退出してからやり直してください。");
            player.closeInventory();
            return;
        }
        if (table == null) {
            table = new Table(player.getUniqueId(), dealer.getUniqueId());
        }
        if (table.phase != Phase.LOBBY) {
            player.sendMessage("§cすでにラウンドが進行中です。");
            player.closeInventory();
            return;
        }
        if (table.bets.size() >= maxPlayers && !table.bets.containsKey(player.getUniqueId())) {
            player.sendMessage("§c卓が満員です（最大 " + maxPlayers + " 人）。");
            player.closeInventory();
            return;
        }
        table.bets.putIfAbsent(player.getUniqueId(), 0);
        player.removeMetadata("perocasino_chinchiro_dealer", plugin);
        broadcastLobby("§a" + player.getName() + " がチンチロ卓に参加しました。");
        openLobby(player);
    }

    private void openLobby(Player player) {
        if (table == null) {
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, LOBBY_TITLE);
        int bet = table.bets.getOrDefault(player.getUniqueId(), 0);
        inv.setItem(10, icon(Material.PLAYER_HEAD, "§b参加者: §f" + table.bets.size() + " / " + maxPlayers, lobbyLore()));
        inv.setItem(11, icon(Material.DIAMOND, "§e掛け金（財布）", List.of("§7現在: §b" + bet + " §7ダイヤ", "§7子はこの額を親と張ります。")));
        List<String> startLore = new ArrayList<>();
        startLore.add("§7親だけが押せます。");
        startLore.add("§7全員が掛け金を選び、サイコロ領域が有効なときだけ開始します。");
        if (table.bets.size() == 1) {
            startLore.add("§7※ 1人のときは §eディーラー（ハウス）§7が子として対戦します。");
        }
        inv.setItem(13, icon(Material.MAGMA_CREAM, "§a§lSTART", startLore));
        inv.setItem(15, icon(Material.BARRIER, "§c退出", null));
        player.openInventory(inv);
    }

    private List<String> lobbyLore() {
        if (table == null) {
            return List.of();
        }
        List<String> lore = new ArrayList<>();
        for (UUID id : table.bets.keySet()) {
            Player p = Bukkit.getPlayer(id);
            String hostMark = id.equals(table.host) ? " §6[親]" : " §7[子]";
            int b = table.bets.getOrDefault(id, 0);
            lore.add("§7- §f" + (p == null ? id.toString().substring(0, 8) : p.getName()) + hostMark + " §7掛け:§b" + b);
        }
        return lore;
    }

    private void openBet(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, BET_TITLE);
        int cur = table == null ? 0 : table.bets.getOrDefault(player.getUniqueId(), 0);
        inv.setItem(4, icon(Material.GOLD_INGOT, "§e現在の掛け金: §b" + cur, List.of(
                "§7財布からラウンド開始時に徴収されます。",
                "§7不足分は借金として計上されます。"
        )));
        inv.setItem(10, icon(Material.DIAMOND, "§b1", null));
        inv.setItem(11, icon(Material.DIAMOND, "§b2", null));
        inv.setItem(12, icon(Material.DIAMOND, "§b5", null));
        inv.setItem(13, icon(Material.DIAMOND, "§b10", null));
        inv.setItem(14, icon(Material.DIAMOND, "§b32", null));
        inv.setItem(15, icon(Material.DIAMOND, "§b64", null));
        inv.setItem(22, icon(Material.ARROW, "§7ロビーへ戻る", null));
        player.openInventory(inv);
    }

    private void setBet(Player player, int amount) {
        if (table == null) {
            return;
        }
        table.bets.put(player.getUniqueId(), amount);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
        player.sendMessage("§a掛け金を §b" + amount + " §aダイヤに設定しました。");
        broadcastLobby("§e" + player.getName() + " の掛け金: §b" + amount);
        openLobby(player);
    }

    private void startRound(Player host) {
        if (table == null || table.phase != Phase.LOBBY) {
            return;
        }
        if (!host.getUniqueId().equals(table.host)) {
            host.sendMessage("§c開始できるのは親だけです。");
            return;
        }
        if (!dice.canRollDice()) {
            host.sendMessage("§cサイコロ表示領域が無効です。§7/perocasino chinchiro region set §cと chinchiro.enabled を確認してください。");
            return;
        }
        if (table.bets.size() < 1) {
            host.sendMessage("§c参加者がいません。");
            return;
        }
        for (UUID id : table.bets.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                host.sendMessage("§cオフラインの参加者がいます。");
                return;
            }
            if (table.bets.getOrDefault(id, 0) <= 0) {
                host.sendMessage("§c全員が掛け金を選んでから開始してください。未設定: " + p.getName());
                return;
            }
        }

        List<UUID> children = new ArrayList<>();
        for (UUID id : table.bets.keySet()) {
            if (!id.equals(table.host)) {
                children.add(id);
            }
        }
        if (children.isEmpty()) {
            if (table.bets.size() == 1 && table.bets.containsKey(table.host)) {
                children.add(HOUSE_CHILD_PLACEHOLDER);
                table.soloVsHouse = true;
            } else {
                host.sendMessage("§c子がいません。");
                return;
            }
        } else {
            table.soloVsHouse = false;
        }

        for (UUID cid : children) {
            if (isHouseChildPlaceholder(cid)) {
                continue;
            }
            int bet = table.bets.getOrDefault(cid, 0);
            Player cp = Bukkit.getPlayer(cid);
            economy.takeBetFromWalletOrDebt(cp, bet);
        }

        table.phase = Phase.ROLLING;
        table.children = children;
        table.childIndex = 0;
        table.oyaDice = null;
        table.refundPending.clear();
        for (UUID cid : children) {
            if (!isHouseChildPlaceholder(cid)) {
                table.refundPending.add(cid);
            }
        }

        cancelRollChain();
        long token = ++rollChainToken;
        if (table.soloVsHouse) {
            broadcastTable("§6[チンチロ] §fラウンド開始。§7対戦: §e" + host.getName()
                    + " §7（親） vs §eディーラー（ハウス）");
        } else {
            broadcastTable("§6[チンチロ] §fラウンド開始。親は §e" + host.getName() + " §fです。");
        }
        schedule(token, rollDelayTicks, () -> rollOyaStep(token));
    }

    private void rollOyaStep(long token) {
        if (!isChainValid(token) || table == null || table.phase != Phase.ROLLING) {
            return;
        }
        Player oya = Bukkit.getPlayer(table.host);
        if (oya == null) {
            abortRoundAndResetLobby("§c親がオフラインになったため卓をリセットしました。");
            return;
        }
        int[] tops = dice.rollThreeDice(oya, true);
        if (tops.length != 3) {
            refundCurrentRoundStakes();
            table.phase = Phase.LOBBY;
            broadcastTable("§cサイコロを表示できませんでした。ラウンドは中断され、子の掛け金は戻しました。");
            return;
        }
        table.oyaDice = tops;
        oya.playSound(oya.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        broadcastTable("§6[チンチロ] §f親 §e" + oya.getName() + " §fの出目: §e"
                + tops[0] + " §7| §e" + tops[1] + " §7| §e" + tops[2]
                + " §7（§f" + ChinchiroHandEvaluator.describeJapanese(tops) + "§7）");
        schedule(token, rollDelayTicks, () -> rollNextChild(token));
    }

    private void rollNextChild(long token) {
        if (!isChainValid(token) || table == null || table.phase != Phase.ROLLING) {
            return;
        }
        if (table.childIndex >= table.children.size()) {
            finishRound(token);
            return;
        }
        UUID cid = table.children.get(table.childIndex);
        if (isHouseChildPlaceholder(cid)) {
            Player oya = Bukkit.getPlayer(table.host);
            if (oya == null) {
                abortRoundAndResetLobby("§c親がオフラインになったため卓をリセットしました。");
                return;
            }
            int[] tops = dice.rollThreeDice(oya, true);
            if (tops.length != 3) {
                refundCurrentRoundStakes();
                table.phase = Phase.LOBBY;
                broadcastTable("§cディーラー（ハウス）の出目を表示できませんでした。ラウンドは中断され、人間の子がいれば掛け金を戻しました。");
                cancelRollChain();
                for (UUID id : new ArrayList<>(table.bets.keySet())) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        openLobby(p);
                    }
                }
                return;
            }
            oya.playSound(oya.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.95f);
            settleHouseVersusOya(oya, tops);
            table.childIndex++;
            schedule(token, rollDelayTicks, () -> rollNextChild(token));
            return;
        }
        Player child = Bukkit.getPlayer(cid);
        if (child == null || !child.isOnline()) {
            int bet = table.bets.getOrDefault(cid, 0);
            economy.addWalletBalance(cid, bet);
            table.refundPending.remove(cid);
            broadcastTable("§e" + Bukkit.getOfflinePlayer(cid).getName() + " §7はオフラインのためスキップし掛け金を返却しました。");
            table.childIndex++;
            schedule(token, 5L, () -> rollNextChild(token));
            return;
        }
        int[] tops = dice.rollThreeDice(child, true);
        if (tops.length != 3) {
            int bet = table.bets.getOrDefault(cid, 0);
            economy.addWalletBalance(cid, bet);
            table.refundPending.remove(cid);
            child.sendMessage("§cあなたの番のサイコロ表示に失敗しました。掛け金は戻しました。");
            table.childIndex++;
            schedule(token, rollDelayTicks, () -> rollNextChild(token));
            return;
        }
        child.playSound(child.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.1f);
        settleChildVersusOya(child, tops);
        table.refundPending.remove(cid);
        table.childIndex++;
        schedule(token, rollDelayTicks, () -> rollNextChild(token));
    }

    private void settleHouseVersusOya(Player oya, int[] houseDice) {
        if (table == null || table.oyaDice == null) {
            return;
        }
        int bet = table.bets.getOrDefault(table.host, 0);
        int cmp = ChinchiroHandEvaluator.compareHands(table.oyaDice, houseDice);
        String oyaDesc = ChinchiroHandEvaluator.describeJapanese(table.oyaDice);
        String houseDesc = ChinchiroHandEvaluator.describeJapanese(houseDice);
        String prefix = "§6[チンチロ] §fディーラー（ハウス）§f: §e"
                + houseDice[0] + " §7| §e" + houseDice[1] + " §7| §e" + houseDice[2]
                + " §7（§f" + houseDesc + "§7） ";
        if (cmp > 0) {
            economy.addWalletBalance(oya.getUniqueId(), bet);
            broadcastTable(prefix + "§c→ 親の勝ち §7（親: " + oyaDesc + "）");
            oya.sendMessage("§aディーラー（ハウス）に勝ちました。§7（+" + bet + "）");
        } else if (cmp < 0) {
            economy.takeBetFromWalletOrDebt(oya, bet);
            broadcastTable(prefix + "§c→ ディーラー（ハウス）の勝ち §7（親: " + oyaDesc + "）");
            oya.sendMessage("§cディーラー（ハウス）に負けました。§7（-" + bet + "）");
        } else {
            broadcastTable(prefix + "§e→ 引き分け");
            oya.sendMessage("§e引き分け。掛け金のやり取りはありません。");
        }
    }

    private void settleChildVersusOya(Player child, int[] childDice) {
        if (table == null || table.oyaDice == null) {
            return;
        }
        Player oya = Bukkit.getPlayer(table.host);
        int bet = table.bets.getOrDefault(child.getUniqueId(), 0);
        int cmp = ChinchiroHandEvaluator.compareHands(table.oyaDice, childDice);
        String oyaDesc = ChinchiroHandEvaluator.describeJapanese(table.oyaDice);
        String koDesc = ChinchiroHandEvaluator.describeJapanese(childDice);
        if (cmp > 0) {
            if (oya != null) {
                economy.addWalletBalance(oya.getUniqueId(), bet);
            }
            broadcastTable("§6[チンチロ] §f子 §e" + child.getName() + " §f: §e"
                    + childDice[0] + " §7| §e" + childDice[1] + " §7| §e" + childDice[2]
                    + " §7（§f" + koDesc + "§7） §c→ 親の勝ち §7（親: " + oyaDesc + "）");
            child.sendMessage("§c親に負けました。§7（-" + bet + "）");
        } else if (cmp < 0) {
            if (oya != null) {
                economy.takeBetFromWalletOrDebt(oya, bet);
                economy.addWalletBalance(child.getUniqueId(), 2 * bet);
                oya.sendMessage("§c" + child.getName() + " に負けました。§7（-" + bet + "）");
            } else {
                economy.addWalletBalance(child.getUniqueId(), 2 * bet);
            }
            broadcastTable("§6[チンチロ] §f子 §e" + child.getName() + " §f: §e"
                    + childDice[0] + " §7| §e" + childDice[1] + " §7| §e" + childDice[2]
                    + " §7（§f" + koDesc + "§7） §a→ 子の勝ち §7（親: " + oyaDesc + "）");
            child.sendMessage("§a親に勝ちました。§7（+" + bet + "）");
        } else {
            economy.addWalletBalance(child.getUniqueId(), bet);
            broadcastTable("§6[チンチロ] §f子 §e" + child.getName() + " §f: §e"
                    + childDice[0] + " §7| §e" + childDice[1] + " §7| §e" + childDice[2]
                    + " §7（§f" + koDesc + "§7） §e→ 引き分け");
            child.sendMessage("§e引き分け。掛け金は戻りました。");
        }
    }

    private void finishRound(long token) {
        if (!isChainValid(token) || table == null) {
            return;
        }
        table.phase = Phase.LOBBY;
        broadcastTable("§6[チンチロ] §fラウンド終了。ロビーに戻ります。");
        for (UUID id : new ArrayList<>(table.bets.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                openLobby(p);
            }
        }
    }

    private void refundCurrentRoundStakes() {
        if (table == null) {
            return;
        }
        for (UUID cid : new ArrayList<>(table.refundPending)) {
            int bet = table.bets.getOrDefault(cid, 0);
            economy.addWalletBalance(cid, bet);
        }
        table.refundPending.clear();
    }

    private void abortRoundAndResetLobby(String message) {
        cancelRollChain();
        if (table == null) {
            return;
        }
        if (table.phase == Phase.ROLLING) {
            refundCurrentRoundStakes();
        }
        table.phase = Phase.LOBBY;
        table.oyaDice = null;
        table.children.clear();
        table.childIndex = 0;
        for (UUID id : new ArrayList<>(table.bets.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
                p.closeInventory();
            }
        }
    }

    private void leaveTable(Player player, boolean voluntary) {
        if (table == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!table.bets.containsKey(id)) {
            return;
        }
        if (table.phase == Phase.ROLLING) {
            player.sendMessage("§c進行中は退出できません。");
            return;
        }
        table.bets.remove(id);
        if (voluntary) {
            player.sendMessage("§eチンチロ卓から退出しました。");
            broadcastLobby("§e" + player.getName() + " が退出しました。");
        }
        if (table.bets.isEmpty()) {
            table = null;
        } else {
            if (id.equals(table.host)) {
                table.host = table.bets.keySet().iterator().next();
                String newHostName = Bukkit.getOfflinePlayer(table.host).getName();
                if (newHostName == null) {
                    newHostName = table.host.toString().substring(0, 8);
                }
                broadcastLobby("§6親が抜けたため、§e" + newHostName + " §6が新しい親になりました。");
            }
        }
        player.closeInventory();
    }

    private boolean resumeToLobby(Player player) {
        if (table == null || !table.bets.containsKey(player.getUniqueId())) {
            return false;
        }
        if (table.phase == Phase.LOBBY) {
            openLobby(player);
            return true;
        }
        return false;
    }

    private void broadcastLobby(String msg) {
        if (table == null) {
            return;
        }
        for (UUID id : table.bets.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private void broadcastTable(String msg) {
        if (table == null) {
            return;
        }
        for (UUID id : table.bets.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private void schedule(long token, long delay, Runnable run) {
        pendingRollTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (token != rollChainToken) {
                return;
            }
            run.run();
        }, delay);
    }

    private boolean isChainValid(long token) {
        return token == rollChainToken;
    }

    private static boolean isTopClick(InventoryClickEvent event) {
        return event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory());
    }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        it.setItemMeta(meta);
        return it;
    }

    private boolean isDealer(Villager villager) {
        String configured = plugin.getConfig().getString("chinchiro.dealer.uuid", "");
        if (configured != null && !configured.isBlank()) {
            try {
                if (villager.getUniqueId().equals(UUID.fromString(configured.trim()))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        String name = villager.getCustomName();
        if (name == null) {
            return false;
        }
        String plain = ChatColor.stripColor(name);
        if (plain == null) {
            return false;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        return lower.contains("chinchiro") || lower.contains("チンチロ") || lower.contains("chin");
    }

    private Entity findDealerFor(Player player) {
        if (table != null) {
            Entity e = Bukkit.getEntity(table.dealerId);
            if (e != null) {
                return e;
            }
        }
        String configured = plugin.getConfig().getString("chinchiro.dealer.uuid", "");
        if (configured != null && !configured.isBlank()) {
            try {
                UUID uuid = UUID.fromString(configured.trim());
                for (Entity e : player.getWorld().getEntities()) {
                    if (e.getUniqueId().equals(uuid)) {
                        return e;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Entity e : player.getNearbyEntities(joinRadius, joinRadius, joinRadius)) {
            if (e instanceof Villager v && isDealer(v)) {
                return v;
            }
        }
        return null;
    }

    private Entity resolveDealerFromMeta(Player player) {
        if (!player.hasMetadata("perocasino_chinchiro_dealer")) {
            return null;
        }
        try {
            String raw = player.getMetadata("perocasino_chinchiro_dealer").get(0).asString();
            UUID id = UUID.fromString(raw);
            return Bukkit.getEntity(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void configureDealerNpc(Villager villager) {
        villager.setAI(false);
        villager.setGravity(false);
        villager.setCustomName("§6Chinchiro Dealer");
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.MASON);
    }

    private enum Phase {
        LOBBY,
        ROLLING
    }

    private static final class Table {
        UUID host;
        UUID dealerId;
        /** 参加順を保持 */
        final Map<UUID, Integer> bets = new LinkedHashMap<>();
        Phase phase = Phase.LOBBY;
        int[] oyaDice;
        List<UUID> children = new ArrayList<>();
        int childIndex;
        final LinkedHashSet<UUID> refundPending = new LinkedHashSet<>();
        boolean soloVsHouse;

        Table(UUID host, UUID dealerId) {
            this.host = host;
            this.dealerId = dealerId;
        }
    }
}
