package me.bokan.perocasino.games.hilo;

import me.bokan.perocasino.economy.EconomyManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 13x4デッキで遊ぶHigh & Low。
 *
 * - H&Lディーラー村人から開始
 * - ディーラー戦 / プレイヤー2人対戦を選択
 * - 5/7/9セットを選択
 * - 親カードは表、子カードは裏(表示名 "?")として頭上表示
 */
public final class HiLoService implements Listener {

    private static final String MODE_TITLE = "§0§lH&L: モード選択";
    private static final String SETS_TITLE = "§0§lH&L: セット数";
    private static final String LOBBY_TITLE = "§0§lH&L: ロビー";
    private static final String BET_TITLE = "§0§lH&L: 掛け金";
    private static final String CHOICE_TITLE = "§0§lH&L: High / Low";

    private static final int REVERSE_MODEL_DATA = 14;

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final NamespacedKey cardKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey rankKey;
    private final NamespacedKey reverseKey;

    private Session session;
    private BukkitTask hudTask;

    public HiLoService(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.cardKey = new NamespacedKey(plugin, "hilo_card");
        this.ownerKey = new NamespacedKey(plugin, "hilo_owner");
        this.rankKey = new NamespacedKey(plugin, "hilo_rank");
        this.reverseKey = new NamespacedKey(plugin, "hilo_reverse");
        startHud();
    }

    public void shutdown() {
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        if (session != null) {
            finishSession("§cHigh & Lowを終了しました。", false);
        }
    }

    public void openFromMenu(Player player) {
        Entity dealer = findDealerFor(player);
        if (dealer == null) {
            player.sendMessage("§cH&Lディーラーが見つかりません。ディーラー村人に話しかけてください。");
            return;
        }
        if (tryJoinExistingLobby(player)) {
            return;
        }
        openMode(player, dealer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!isDealer(villager)) return;
        event.setCancelled(true);
        if (tryJoinExistingLobby(event.getPlayer())) {
            return;
        }
        openMode(event.getPlayer(), villager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Villager villager && isDealer(villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (session == null) return;
        UUID id = event.getPlayer().getUniqueId();
        if (session.players.containsKey(id)) {
            leave(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isHiLoCard(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isHiLoCard(event.getMainHandItem()) || isHiLoCard(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isHiLoCard(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!MODE_TITLE.equals(title)
                && !SETS_TITLE.equals(title)
                && !LOBBY_TITLE.equals(title)
                && !BET_TITLE.equals(title)
                && !CHOICE_TITLE.equals(title)) {
            if (isHiLoCard(event.getCurrentItem()) || isHiLoCard(event.getCursor())) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        if (MODE_TITLE.equals(title)) {
            if (event.getSlot() == 11) openSetSelect(player, Mode.DEALER);
            else if (event.getSlot() == 15) openSetSelect(player, Mode.PVP);
            else if (event.getSlot() == 22) player.closeInventory();
            return;
        }

        if (SETS_TITLE.equals(title)) {
            PendingMode pending = pendingModes.remove(player.getUniqueId());
            if (pending == null) {
                player.closeInventory();
                return;
            }
            int sets = switch (event.getSlot()) {
                case 11 -> 5;
                case 13 -> 7;
                case 15 -> 9;
                default -> 0;
            };
            if (sets > 0) createLobby(player, pending.mode, pending.dealerId, sets);
            else if (event.getSlot() == 22) player.closeInventory();
            return;
        }

        if (LOBBY_TITLE.equals(title)) {
            if (session == null || !session.players.containsKey(player.getUniqueId())) {
                player.closeInventory();
                return;
            }
            switch (event.getSlot()) {
                case 10 -> openBet(player);
                case 13 -> startIfHost(player);
                case 15 -> leave(player, true);
                default -> {
                }
            }
            return;
        }

        if (BET_TITLE.equals(title)) {
            if (session == null || session.phase != Phase.LOBBY || !session.players.containsKey(player.getUniqueId())) {
                player.closeInventory();
                return;
            }
            int amount = switch (event.getSlot()) {
                case 10 -> 1;
                case 11 -> 5;
                case 12 -> 10;
                case 13 -> 32;
                case 14 -> 64;
                case 15 -> countHandCoins(player);
                default -> 0;
            };
            if (amount > 0) setBet(player, amount);
            else if (event.getSlot() == 22) openLobby(player);
            return;
        }

        if (CHOICE_TITLE.equals(title)) {
            if (event.getSlot() == 11) choose(player, Guess.HIGH);
            else if (event.getSlot() == 15) choose(player, Guess.LOW);
        }
    }

    private final Map<UUID, PendingMode> pendingModes = new HashMap<>();

    private boolean tryJoinExistingLobby(Player player) {
        if (session == null || session.phase != Phase.LOBBY) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (session.players.containsKey(id)) {
            openLobby(player);
            return true;
        }
        if (session.mode == Mode.DEALER) {
            player.sendMessage("§c現在のH&Lロビーはディーラー戦です。終了後に参加してください。");
            return true;
        }
        if (session.players.size() >= 2) {
            player.sendMessage("§c現在のH&Lロビーは満員です。");
            return true;
        }
        session.players.put(id, new PlayerState(player));
        rememberName(player);
        broadcastLobby("§a" + player.getName() + " がH&Lロビーに参加しました。");
        openLobby(player);
        updateNameDisplays();
        return true;
    }

    private void openMode(Player player, Entity dealer) {
        if (session != null && session.phase == Phase.PLAYING && !session.players.containsKey(player.getUniqueId())) {
            player.sendMessage("§c現在H&Lは進行中です。次のゲームまでお待ちください。");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, MODE_TITLE);
        inv.setItem(11, icon(Material.EMERALD, "§aディーラー戦", List.of("§7NPCディーラーと対戦します。")));
        inv.setItem(15, icon(Material.PLAYER_HEAD, "§bプレイヤー2人対戦", List.of("§7もう1人を待って対戦します。")));
        inv.setItem(22, icon(Material.BARRIER, "§7閉じる", null));
        pendingModes.put(player.getUniqueId(), new PendingMode(Mode.DEALER, dealer.getUniqueId()));
        player.openInventory(inv);
    }

    private void openSetSelect(Player player, Mode mode) {
        PendingMode current = pendingModes.get(player.getUniqueId());
        UUID dealerId = current == null ? findDealerIdFor(player) : current.dealerId;
        pendingModes.put(player.getUniqueId(), new PendingMode(mode, dealerId));
        Inventory inv = Bukkit.createInventory(null, 27, SETS_TITLE);
        inv.setItem(11, icon(Material.PAPER, "§e5セット", List.of("§7短めの勝負")));
        inv.setItem(13, icon(Material.PAPER, "§e7セット", List.of("§7標準")));
        inv.setItem(15, icon(Material.PAPER, "§e9セット", List.of("§7長めの勝負")));
        inv.setItem(22, icon(Material.ARROW, "§7戻る", null));
        player.openInventory(inv);
    }

    private void createLobby(Player host, Mode mode, UUID dealerId, int sets) {
        if (session != null && session.phase == Phase.PLAYING) {
            host.sendMessage("§c進行中のH&Lがあります。");
            return;
        }
        session = new Session(host.getUniqueId(), dealerId, mode, sets);
        session.players.put(host.getUniqueId(), new PlayerState(host));
        rememberName(host);
        openLobby(host);
        broadcastLobby("§a" + host.getName() + " がH&Lロビーを作成しました。");
        updateNameDisplays();
    }

    private void openLobby(Player player) {
        if (session == null) return;
        PlayerState ps = session.players.get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, LOBBY_TITLE);
        inv.setItem(10, icon(Material.DIAMOND, "§e掛け金を選ぶ", List.of("§7現在: §b" + (ps == null ? 0 : ps.bet) + "個")));
        inv.setItem(11, icon(Material.PLAYER_HEAD, "§b参加者: §f" + session.players.size(), lobbyLore()));
        inv.setItem(13, icon(Material.EMERALD_BLOCK, "§a§lSTART", List.of(
                "§7ホストのみ開始できます。",
                "§7モード: §f" + (session.mode == Mode.DEALER ? "ディーラー戦" : "2人対戦"),
                "§7セット数: §e" + session.maxSets
        )));
        inv.setItem(15, icon(Material.BARRIER, "§c退出", null));
        player.openInventory(inv);
    }

    private List<String> lobbyLore() {
        if (session == null) return List.of();
        List<String> lore = new ArrayList<>();
        for (UUID id : session.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = session.players.get(id);
            lore.add("§7- §f" + (p == null ? id.toString().substring(0, 8) : p.getName())
                    + (id.equals(session.host) ? " §6[HOST]" : "")
                    + " §7bet=§b" + (ps == null ? 0 : ps.bet));
        }
        return lore;
    }

    private void openBet(Player player) {
        PlayerState ps = session == null ? null : session.players.get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, BET_TITLE);
        inv.setItem(4, icon(Material.GOLD_INGOT, "§e現在の掛け金: §b" + (ps == null ? 0 : ps.bet) + "個", List.of(
                "§7財布ではなく手持ちビーストコインから取ります。",
                "§7選び直すと前の掛け金は手持ちへ戻ります。"
        )));
        inv.setItem(10, icon(Material.DIAMOND, "§b1個", null));
        inv.setItem(11, icon(Material.DIAMOND, "§b5個", null));
        inv.setItem(12, icon(Material.DIAMOND, "§b10個", null));
        inv.setItem(13, icon(Material.DIAMOND, "§b32個", null));
        inv.setItem(14, icon(Material.DIAMOND, "§b64個", null));
        inv.setItem(15, icon(Material.DIAMOND_BLOCK, "§b手持ち全部", List.of("§7現在: " + countHandCoins(player))));
        inv.setItem(22, icon(Material.ARROW, "§7戻る", null));
        player.openInventory(inv);
    }

    private void setBet(Player player, int amount) {
        PlayerState ps = session.players.get(player.getUniqueId());
        if (ps == null) return;
        if (ps.bet > 0) {
            addCoins(player, ps.bet);
            ps.bet = 0;
        }
        int hand = countHandCoins(player);
        if (hand < amount) {
            player.sendMessage("§c手持ちビーストコインが足りません。必要: " + amount + " / 手持ち: " + hand);
            openBet(player);
            return;
        }
        removeCoins(player, amount);
        ps.bet = amount;
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.25f);
        openLobby(player);
        broadcastLobby("§e" + player.getName() + " の掛け金: §b" + amount + "個");
        updateNameDisplays();
    }

    private void startIfHost(Player player) {
        if (session == null || session.phase != Phase.LOBBY) return;
        if (!player.getUniqueId().equals(session.host)) {
            player.sendMessage("§c開始できるのはホストだけです。");
            return;
        }
        int required = session.mode == Mode.PVP ? 2 : 1;
        if (session.players.size() != required) {
            player.sendMessage(session.mode == Mode.PVP
                    ? "§c2人対戦は参加者が2人必要です。"
                    : "§c参加者が必要です。");
            return;
        }
        for (UUID id : session.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = session.players.get(id);
            if (p == null || !p.isOnline()) {
                player.sendMessage("§cオフライン参加者がいます。");
                return;
            }
            if (ps.bet <= 0) {
                player.sendMessage("§c全員が掛け金を選んでください。未設定: " + p.getName());
                return;
            }
            if (freeSlots(p) < 3) {
                player.sendMessage("§c" + p.getName() + " のインベントリ空きが少なすぎます。");
                return;
            }
        }
        startGame();
    }

    private void startGame() {
        session.phase = Phase.PLAYING;
        session.deck = buildDeck();
        session.setIndex = 0;
        session.parentIndex = 0;
        for (PlayerState ps : session.players.values()) {
            ps.points = 0;
            ps.originalBet = ps.bet;
            ps.parentCard = null;
            ps.childCard = null;
            ps.guess = null;
            ps.awaitingChoice = false;
        }
        broadcast("§aHigh & Low開始！ §e" + session.maxSets + "セット§aで勝負します。");
        nextSet();
    }

    private void nextSet() {
        if (session == null || session.phase != Phase.PLAYING) return;
        if (session.setIndex >= session.maxSets) {
            settle();
            return;
        }
        List<UUID> order = new ArrayList<>(session.players.keySet());
        UUID parentId = order.get(session.parentIndex % order.size());
        UUID childId = session.mode == Mode.DEALER
                ? parentId
                : order.get((session.parentIndex + 1) % order.size());
        session.currentParent = parentId;
        session.currentChild = childId;

        PlayerState parent = session.players.get(parentId);
        PlayerState child = session.players.get(childId);
        parent.parentCard = draw();
        child.childCard = draw();
        child.guess = null;
        child.awaitingChoice = true;

        giveDisplayCard(Bukkit.getPlayer(parentId), parent.parentCard, false);
        Player childPlayer = Bukkit.getPlayer(childId);
        giveDisplayCard(childPlayer, child.childCard, true);
        updateNameDisplays();

        broadcast("§d[H&L] §fセット §e" + (session.setIndex + 1) + "/" + session.maxSets
                + " §7親: §f" + playerName(parentId)
                + " §7表カード: §e" + parent.parentCard.label());
        if (childPlayer != null) openChoice(childPlayer);
    }

    private void openChoice(Player player) {
        PlayerState ps = session.players.get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, CHOICE_TITLE);
        inv.setItem(4, icon(Material.PAPER, "§e親カード: §f" + session.players.get(session.currentParent).parentCard.label(), List.of(
                "§7自分の伏せカードが親より高いか低いか選択。",
                "§7同じ数字は不正解扱いです。"
        )));
        inv.setItem(11, icon(Material.LIME_CONCRETE, "§a§lHIGH", List.of("§7子カードが親カードより高い")));
        inv.setItem(15, icon(Material.RED_CONCRETE, "§c§lLOW", List.of("§7子カードが親カードより低い")));
        inv.setItem(22, icon(Material.DIAMOND, "§b現在ポイント: §f" + ps.points, null));
        player.openInventory(inv);
    }

    private void choose(Player player, Guess guess) {
        if (session == null || session.phase != Phase.PLAYING) return;
        if (!player.getUniqueId().equals(session.currentChild)) {
            player.sendMessage("§e今選ぶのは子の番です。");
            return;
        }
        PlayerState child = session.players.get(player.getUniqueId());
        PlayerState parent = session.players.get(session.currentParent);
        if (child == null || parent == null || !child.awaitingChoice) return;
        child.guess = guess;
        child.awaitingChoice = false;

        int parentRank = parent.parentCard.rank.value;
        int childRank = child.childCard.rank.value;
        boolean hit = (guess == Guess.HIGH && childRank > parentRank)
                || (guess == Guess.LOW && childRank < parentRank);
        if (hit) {
            child.points++;
            player.sendMessage("§a正解！ §7親: §f" + parent.parentCard.label()
                    + " §7子: §f" + child.childCard.label() + " §a+1pt");
        } else {
            player.sendMessage("§c不正解。 §7親: §f" + parent.parentCard.label()
                    + " §7子: §f" + child.childCard.label());
        }
        revealChildCard(player, child);
        updateNameDisplays();

        session.setIndex++;
        session.parentIndex++;
        Bukkit.getScheduler().runTaskLater(plugin, this::nextSet, 30L);
    }

    private void settle() {
        if (session == null) return;
        session.phase = Phase.FINISHED;
        if (session.mode == Mode.DEALER) {
            UUID id = session.players.keySet().iterator().next();
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = session.players.get(id);
            int dealerPoints = session.maxSets - ps.points;
            int diff = Math.abs(ps.points - dealerPoints);
            if (p != null) {
                if (ps.points > dealerPoints) {
                    int payout = calcDealerPayout(ps.originalBet, diff);
                    addCoins(p, payout);
                    p.sendMessage("§a[H&L] 勝ち！ §7あなた " + ps.points + " / ディーラー " + dealerPoints
                            + " §f配当: §b" + payout);
                } else if (ps.points == dealerPoints) {
                    addCoins(p, ps.originalBet);
                    p.sendMessage("§e[H&L] 同点。掛け金を返却しました。");
                } else {
                    int penalty = diff >= 2 ? calcDealerPayout(ps.originalBet, diff) : 0;
                    if (penalty > 0) chargeWalletOrDebt(p, penalty);
                    p.sendMessage("§c[H&L] 負け。掛け金没収"
                            + (penalty > 0 ? " / 追加徴収: " + penalty : ""));
                }
            }
        } else {
            List<UUID> ids = new ArrayList<>(session.players.keySet());
            PlayerState a = session.players.get(ids.get(0));
            PlayerState b = session.players.get(ids.get(1));
            Player pa = Bukkit.getPlayer(ids.get(0));
            Player pb = Bukkit.getPlayer(ids.get(1));
            settlePvp(pa, a, pb, b);
        }
        finishSession("§7H&L終了。", false);
    }

    private void settlePvp(Player aPlayer, PlayerState a, Player bPlayer, PlayerState b) {
        if (aPlayer == null || bPlayer == null) return;
        int diff = Math.abs(a.points - b.points);
        if (a.points == b.points) {
            addCoins(aPlayer, a.originalBet);
            addCoins(bPlayer, b.originalBet);
            broadcast("§e[H&L] 同点。両者の掛け金を返却しました。");
            return;
        }
        Player winner = a.points > b.points ? aPlayer : bPlayer;
        Player loser = a.points > b.points ? bPlayer : aPlayer;
        PlayerState ws = a.points > b.points ? a : b;
        PlayerState ls = a.points > b.points ? b : a;
        int payout = calcDealerPayout(ws.originalBet, diff);
        addCoins(winner, payout);
        economy.addWalletBalance(winner.getUniqueId(), ls.originalBet);
        int penalty = diff >= 2 ? calcDealerPayout(ls.originalBet, diff) : 0;
        if (penalty > 0) chargeWalletOrDebt(loser, penalty);
        winner.sendMessage("§a[H&L] 勝ち！ 配当 §b" + payout + " §a+ 相手没収分を財布へ §b" + ls.originalBet);
        loser.sendMessage("§c[H&L] 負け。掛け金没収" + (penalty > 0 ? " / 追加徴収: " + penalty : ""));
    }

    private int calcDealerPayout(int bet, int diff) {
        return (int) Math.floor(bet * (diff + 1) * 1.5d);
    }

    private void chargeWalletOrDebt(Player player, int amount) {
        UUID id = player.getUniqueId();
        int wallet = economy.getWalletBalance(id);
        int fromWallet = Math.min(wallet, amount);
        if (fromWallet > 0) economy.setWalletBalance(id, wallet - fromWallet);
        int debt = amount - fromWallet;
        if (debt > 0) {
            economy.addDebt(id, debt);
            long now = System.currentTimeMillis();
            if (economy.getLoanDeadline(id) <= 0L) economy.setLoanDeadline(id, now + 24L * 60L * 60L * 1000L);
            if (economy.getNextInterestMillis(id) <= 0L) economy.setNextInterestMillis(id, now + 60L * 60L * 1000L);
            player.sendMessage("§c財布不足のため §e" + debt + " §cを強制借入しました。");
        }
    }

    private List<Card> buildDeck() {
        List<Card> deck = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (Rank rank : Rank.values()) deck.add(new Card(rank));
        }
        Collections.shuffle(deck);
        return deck;
    }

    private Card draw() {
        if (session.deck.isEmpty()) session.deck = buildDeck();
        return session.deck.remove(session.deck.size() - 1);
    }

    private void giveDisplayCard(Player player, Card card, boolean reverse) {
        if (player == null) return;
        ItemStack item = reverse ? reverseItem(player.getUniqueId()) : cardItem(player.getUniqueId(), card);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), item);
    }

    private void revealChildCard(Player player, PlayerState ps) {
        if (player == null) return;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isReverseCard(item)) item.setAmount(0);
        }
        player.getInventory().addItem(cardItem(player.getUniqueId(), ps.childCard));
    }

    private ItemStack cardItem(UUID owner, Card card) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§f§l" + card.label());
        meta.setLore(List.of("§7High & Low Card", "§7数値: §e" + card.rank.value, "§8ゲーム中カード"));
        meta.setCustomModelData(card.rank.ordinal() + 1);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cardKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        pdc.set(rankKey, PersistentDataType.STRING, card.rank.name());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack reverseItem(UUID owner) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§f§l?");
        meta.setLore(List.of("§7High & Low Reverse Card", "§7選択後に公開されます。"));
        meta.setCustomModelData(REVERSE_MODEL_DATA);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cardKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(reverseKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        it.setItemMeta(meta);
        return it;
    }

    private boolean isHiLoCard(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(cardKey, PersistentDataType.BYTE);
    }

    private boolean isReverseCard(ItemStack item) {
        return isHiLoCard(item)
                && item.getItemMeta().getPersistentDataContainer().has(reverseKey, PersistentDataType.BYTE);
    }

    private void updateNameDisplays() {
        if (session == null) return;
        for (UUID id : session.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = session.players.get(id);
            if (p == null || ps == null) continue;
            String card = "";
            if (id.equals(session.currentParent) && ps.parentCard != null) card = " 親:" + ps.parentCard.label();
            if (id.equals(session.currentChild) && ps.childCard != null) card = " 子:?";
            p.setCustomName("§f" + p.getName() + " §7[H&L " + ps.points + "pt bet " + ps.bet + card + "§7]");
            p.setCustomNameVisible(true);
        }
        Entity dealer = getDealerEntity();
        if (dealer != null) {
            if (session.phase == Phase.PLAYING && session.mode == Mode.DEALER) {
                PlayerState ps = session.players.get(session.currentParent);
                String card = ps == null || ps.parentCard == null ? "" : " §7[" + ps.parentCard.label() + " vs ?]";
                dealer.setCustomName("§6H&L Dealer" + card);
            } else {
                dealer.setCustomName("§6H&L Dealer");
            }
            dealer.setCustomNameVisible(true);
        }
    }

    private void startHud() {
        hudTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (session == null) return;
                for (UUID id : session.players.keySet()) {
                    Player p = Bukkit.getPlayer(id);
                    PlayerState ps = session.players.get(id);
                    if (p == null || ps == null) continue;
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText("§eH&L §7| bet §b" + ps.bet
                                    + " §7| point §a" + ps.points
                                    + " §7| set §f" + Math.min(session.setIndex + 1, session.maxSets) + "/" + session.maxSets));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void leave(Player player, boolean notify) {
        if (session == null) return;
        PlayerState ps = session.players.remove(player.getUniqueId());
        if (ps == null) return;
        if (session.phase == Phase.LOBBY && ps.bet > 0) addCoins(player, ps.bet);
        removeCards(player);
        restoreName(player, ps);
        if (notify) player.sendMessage("§7H&Lから退出しました。");
        if (session.players.isEmpty() || session.phase == Phase.PLAYING) {
            finishSession("§cH&Lを終了しました。", true);
        }
    }

    private void finishSession(String msg, boolean refundLobbyBets) {
        if (session == null) return;
        for (UUID id : new ArrayList<>(session.players.keySet())) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = session.players.get(id);
            if (p == null || ps == null) continue;
            if (refundLobbyBets && session.phase == Phase.LOBBY && ps.bet > 0) addCoins(p, ps.bet);
            removeCards(p);
            restoreName(p, ps);
            p.closeInventory();
            p.sendMessage(msg);
        }
        Entity dealer = getDealerEntity();
        if (dealer != null) {
            dealer.setCustomName("§6H&L Dealer");
            dealer.setCustomNameVisible(true);
        }
        session = null;
    }

    private void removeCards(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isHiLoCard(item)) item.setAmount(0);
        }
    }

    private void rememberName(Player player) {
        PlayerState ps = session.players.get(player.getUniqueId());
        if (ps == null || ps.nameRemembered) return;
        ps.originalCustomName = player.getCustomName();
        ps.originalCustomNameVisible = player.isCustomNameVisible();
        ps.nameRemembered = true;
    }

    private void restoreName(Player player, PlayerState ps) {
        if (!ps.nameRemembered) return;
        player.setCustomName(ps.originalCustomName);
        player.setCustomNameVisible(ps.originalCustomNameVisible);
    }

    private boolean isDealer(Villager villager) {
        String configured = plugin.getConfig().getString("hilo.dealer.uuid", "");
        if (configured != null && !configured.isBlank() && configured.equals(villager.getUniqueId().toString())) return true;
        String plain = org.bukkit.ChatColor.stripColor(villager.getCustomName());
        if (plain == null) return false;
        String lower = plain.toLowerCase(Locale.ROOT);
        return lower.contains("hilo")
                || lower.contains("h&l")
                || lower.contains("high")
                || lower.contains("low")
                || plain.contains("ハイロー")
                || plain.contains("H＆L")
                || plain.contains("H&L");
    }

    private Entity findDealerFor(Player player) {
        UUID configured = findDealerIdFor(player);
        if (configured != null) {
            Entity e = Bukkit.getEntity(configured);
            if (e instanceof Villager) return e;
        }
        for (Entity e : player.getNearbyEntities(16, 8, 16)) {
            if (e instanceof Villager v && isDealer(v)) return v;
        }
        return null;
    }

    private UUID findDealerIdFor(Player player) {
        String raw = plugin.getConfig().getString("hilo.dealer.uuid", "");
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Entity getDealerEntity() {
        if (session == null || session.dealerId == null) return null;
        return Bukkit.getEntity(session.dealerId);
    }

    private void broadcastLobby(String msg) {
        if (session == null) return;
        for (UUID id : session.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void broadcast(String msg) {
        if (session == null) return;
        for (UUID id : session.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(msg);
        }
    }

    private String playerName(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p == null ? id.toString().substring(0, 8) : p.getName();
    }

    private ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private int countHandCoins(Player player) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == Material.DIAMOND) n += it.getAmount();
        }
        return n;
    }

    private void removeCoins(Player player, int amount) {
        int left = amount;
        for (ItemStack it : player.getInventory().getContents()) {
            if (left <= 0) break;
            if (it == null || it.getType() != Material.DIAMOND) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
        }
    }

    private void addCoins(Player player, int amount) {
        if (amount <= 0) return;
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(Material.DIAMOND, n));
            if (overflow.isEmpty()) {
                left -= n;
            } else {
                int rem = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                left -= (n - rem);
                break;
            }
        }
        if (left > 0) economy.addWalletBalance(player.getUniqueId(), left);
    }

    private int freeSlots(Player player) {
        int n = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it == null || it.getType() == Material.AIR) n++;
        }
        return n;
    }

    private enum Mode { DEALER, PVP }
    private enum Phase { LOBBY, PLAYING, FINISHED }
    private enum Guess { HIGH, LOW }

    private record PendingMode(Mode mode, UUID dealerId) {}
    private record Card(Rank rank) {
        String label() { return rank.label; }
    }

    private enum Rank {
        ACE("A", 1),
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 8),
        NINE("9", 9),
        TEN("10", 10),
        JACK("J", 11),
        QUEEN("Q", 12),
        KING("K", 13);

        private final String label;
        private final int value;

        Rank(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    private static final class Session {
        private final UUID host;
        private final UUID dealerId;
        private final Mode mode;
        private final int maxSets;
        private final Map<UUID, PlayerState> players = new HashMap<>();
        private List<Card> deck = new ArrayList<>();
        private Phase phase = Phase.LOBBY;
        private int setIndex;
        private int parentIndex;
        private UUID currentParent;
        private UUID currentChild;

        private Session(UUID host, UUID dealerId, Mode mode, int maxSets) {
            this.host = host;
            this.dealerId = dealerId;
            this.mode = mode;
            this.maxSets = maxSets;
        }
    }

    private static final class PlayerState {
        private int bet;
        private int originalBet;
        private int points;
        private Card parentCard;
        private Card childCard;
        private Guess guess;
        private boolean awaitingChoice;
        private boolean nameRemembered;
        private String originalCustomName;
        private boolean originalCustomNameVisible;

        private PlayerState(Player player) {
            originalCustomName = player.getCustomName();
            originalCustomNameVisible = player.isCustomNameVisible();
            nameRemembered = true;
        }
    }
}
