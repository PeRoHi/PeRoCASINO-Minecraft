package me.bokan.perocasino.games.blackjack;

import me.bokan.perocasino.economy.EconomyManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Villager dealer driven blackjack table.
 *
 * Core flow:
 * dealer villager click -> Yes/No -> lobby -> hand-coin bet -> host start -> hand cards + action GUI.
 */
public final class BlackjackService implements Listener {

    private static final String CONFIRM_TITLE = "§0§lBLACKJACK: 開始確認";
    private static final String LOBBY_TITLE = "§0§lBLACKJACK: ロビー";
    private static final String BET_TITLE = "§0§lBLACKJACK: 掛け金";
    private static final String ACTION_TITLE = "§0§lBLACKJACK: 操作";

    /** resourcepack: paper の custom_model_data 1〜13 が textures/item/1.png〜13.png に対応 */
    private final JavaPlugin plugin;
    @SuppressWarnings("unused")
    private final EconomyManager economy;
    private final NamespacedKey cardKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey rankKey;

    private Table table;
    private BukkitTask hudTask;

    public BlackjackService(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.cardKey = new NamespacedKey(plugin, "blackjack_card");
        this.ownerKey = new NamespacedKey(plugin, "blackjack_owner");
        this.rankKey = new NamespacedKey(plugin, "blackjack_rank");
        startHud();
    }

    public void shutdown() {
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        if (table != null) {
            endTable("§cブラックジャックを終了しました。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!isDealer(villager)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (table != null && table.phase == Phase.PLAYING && !table.players.containsKey(player.getUniqueId())) {
            player.sendMessage("§c現在ブラックジャックは進行中です。次のゲームまでお待ちください。");
            return;
        }
        openConfirm(player, villager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!isDealer(villager)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (CONFIRM_TITLE.equals(title)) {
            event.setCancelled(true);
            if (!isTopClick(event)) return;
            if (event.getSlot() == 11) {
                Entity dealer = findDealerFor(player);
                if (dealer == null) {
                    player.sendMessage("§cブラックジャックディーラーが見つかりません。");
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
            if (!isTopClick(event)) return;
            UUID id = player.getUniqueId();
            if (table == null || !table.players.containsKey(id)) {
                player.closeInventory();
                return;
            }
            switch (event.getSlot()) {
                case 11 -> openBet(player);
                case 13 -> {
                    if (!id.equals(table.host)) {
                        player.sendMessage("§e開始できるのは最初にYesを押したホストだけです。");
                        return;
                    }
                    startGame(player);
                }
                case 15 -> leaveTable(player, true);
                default -> {
                }
            }
            return;
        }

        if (BET_TITLE.equals(title)) {
            event.setCancelled(true);
            if (!isTopClick(event)) return;
            if (table == null || table.phase != Phase.LOBBY || !table.players.containsKey(player.getUniqueId())) {
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
            if (amount > 0) {
                setBet(player, amount);
            } else if (event.getSlot() == 22) {
                openLobby(player);
            }
            return;
        }

        if (ACTION_TITLE.equals(title)) {
            event.setCancelled(true);
            if (!isTopClick(event)) return;
            if (table == null || table.phase != Phase.PLAYING) {
                player.closeInventory();
                return;
            }
            PlayerState ps = table.players.get(player.getUniqueId());
            if (ps == null || ps.isDone()) {
                player.sendMessage("§eあなたの操作は完了しています。");
                return;
            }
            switch (event.getSlot()) {
                case 10 -> hit(player);
                case 12 -> stand(player);
                case 14 -> doubleDown(player);
                case 16 -> switchAceMode(player);
                case 22 -> surrender(player);
                default -> {
                }
            }
            return;
        }

        if (isBlackjackCard(event.getCurrentItem()) || isBlackjackCard(event.getCursor())) {
            event.setCancelled(true);
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (isBlackjackCard(hotbar)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isBlackjackCard(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isBlackjackCard(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isBlackjackCard(event.getMainHandItem()) || isBlackjackCard(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (isBlackjackCard(next)) {
            event.getPlayer().sendMessage("§7ブラックジャックカードは操作GUIから使ってください。");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leaveTable(event.getPlayer(), false);
    }

    public void openJoinConfirm(Player player) {
        Entity dealer = findDealerFor(player);
        if (dealer == null) {
            player.sendMessage("§cブラックジャックディーラーが見つかりません。ディーラー村人に話しかけてください。");
            return;
        }
        openConfirm(player, dealer);
    }

    private void openConfirm(Player player, Entity dealer) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);
        inv.setItem(11, icon(Material.LIME_CONCRETE, "§a§lYes", List.of("§7ブラックジャックに参加します。")));
        inv.setItem(15, icon(Material.RED_CONCRETE, "§c§lNo", List.of("§7参加しません。")));
        inv.setItem(22, icon(Material.BARRIER, "§7閉じる", null));
        player.openInventory(inv);
    }

    private void joinLobby(Player player, Entity dealer) {
        if (table == null || table.phase == Phase.FINISHED) {
            table = new Table(player.getUniqueId(), dealer.getUniqueId());
        }
        if (table.phase != Phase.LOBBY) {
            player.sendMessage("§cすでにゲームが開始されています。");
            player.closeInventory();
            return;
        }
        table.players.computeIfAbsent(player.getUniqueId(), id -> new PlayerState(player));
        rememberName(player);
        broadcastLobby("§a" + player.getName() + " がブラックジャックに参加しました。");
        openLobby(player);
        updateNameDisplays();
    }

    private void openLobby(Player player) {
        if (table == null) return;
        PlayerState ps = table.players.get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, LOBBY_TITLE);
        inv.setItem(10, icon(Material.PLAYER_HEAD, "§b参加人数: §f" + table.players.size(), lobbyLore()));
        inv.setItem(11, icon(Material.DIAMOND, "§e掛け金を選ぶ", List.of("§7現在: §b" + (ps == null ? 0 : ps.bet) + " 個")));
        inv.setItem(13, icon(Material.EMERALD_BLOCK, "§a§lSTART", List.of(
                "§7最初にYesを押した人だけ開始できます。",
                "§7全員が掛け金を選んでから押してください。"
        )));
        inv.setItem(15, icon(Material.BARRIER, "§c退出", null));
        player.openInventory(inv);
    }

    private List<String> lobbyLore() {
        if (table == null) return List.of();
        List<String> lore = new ArrayList<>();
        for (UUID id : table.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = table.players.get(id);
            String host = id.equals(table.host) ? " §6[HOST]" : "";
            lore.add("§7- §f" + (p == null ? id.toString().substring(0, 8) : p.getName()) + host
                    + " §7bet=§b" + (ps == null ? 0 : ps.bet));
        }
        return lore;
    }

    private void openBet(Player player) {
        PlayerState ps = table == null ? null : table.players.get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, BET_TITLE);
        inv.setItem(4, icon(Material.GOLD_INGOT, "§e現在の掛け金: §b" + (ps == null ? 0 : ps.bet), List.of(
                "§7掛け金は財布ではなく手持ちのビーストコインから取ります。",
                "§7選び直すと前の掛け金は手持ちへ戻ります。"
        )));
        inv.setItem(10, icon(Material.DIAMOND, "§b1 個", null));
        inv.setItem(11, icon(Material.DIAMOND, "§b5 個", null));
        inv.setItem(12, icon(Material.DIAMOND, "§b10 個", null));
        inv.setItem(13, icon(Material.DIAMOND, "§b32 個", null));
        inv.setItem(14, icon(Material.DIAMOND, "§b64 個", null));
        inv.setItem(15, icon(Material.DIAMOND_BLOCK, "§b手持ち全部", List.of("§7現在の手持ち: " + countHandCoins(player))));
        inv.setItem(22, icon(Material.ARROW, "§7ロビーへ戻る", null));
        player.openInventory(inv);
    }

    private void setBet(Player player, int amount) {
        PlayerState ps = table.players.get(player.getUniqueId());
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
        ps.baseBet = amount;
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
        player.sendMessage("§a掛け金を §b" + amount + " §a個に設定しました。");
        openLobby(player);
        broadcastLobby("§e" + player.getName() + " の掛け金: §b" + amount + "個");
        updateNameDisplays();
    }

    private void startGame(Player host) {
        if (table == null || table.phase != Phase.LOBBY) return;
        if (!host.getUniqueId().equals(table.host)) {
            host.sendMessage("§c開始できるのはホストだけです。");
            return;
        }
        if (table.players.isEmpty()) {
            host.sendMessage("§c参加者がいません。");
            return;
        }
        for (UUID id : table.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = table.players.get(id);
            if (p == null || !p.isOnline()) {
                host.sendMessage("§cオフライン参加者がいます。");
                return;
            }
            if (ps.bet <= 0) {
                host.sendMessage("§c全員が掛け金を選んでから開始してください。未設定: " + p.getName());
                return;
            }
            if (freeSlots(p) < 4) {
                host.sendMessage("§c" + p.getName() + " のインベントリ空きが少なすぎます（最低4枠）。");
                return;
            }
        }

        table.phase = Phase.PLAYING;
        table.deck = buildDeck(table.players.size());
        table.dealerHand.clear();
        table.dealerHand.add(draw());
        table.dealerHand.add(draw()); // hidden until settlement

        for (UUID id : table.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = table.players.get(id);
            ps.hand.clear();
            ps.cardSlots.clear();
            ps.stood = false;
            ps.busted = false;
            ps.doubled = false;
            ps.surrendered = false;
            ps.aceHigh = true;
            ps.baseBet = ps.bet;
            giveCard(p, ps, draw());
            giveCard(p, ps, draw());
            if (score(ps) == 21) {
                ps.stood = true;
                p.sendMessage("§e初手21です。これ以上カードは引けません。");
            }
            openAction(p);
        }
        broadcastTable("§aブラックジャック開始！ カードを配りました。");
        updateNameDisplays();
        checkEnd();
    }

    private List<Card> buildDeck(int playerCount) {
        int copies = Math.max(4, playerCount + 1); // dealer included; 3 players以下は13*4
        List<Card> deck = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    private Card draw() {
        if (table.deck.isEmpty()) {
            table.deck = buildDeck(table.players.size());
        }
        return table.deck.remove(table.deck.size() - 1);
    }

    private void openAction(Player player) {
        PlayerState ps = table.players.get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, ACTION_TITLE);
        inv.setItem(4, icon(Material.PAPER, "§eあなたの掛け金: §b" + ps.bet + "個", List.of(
                "§7手札: " + cardsText(ps.hand),
                "§7合計: " + score(ps),
                "§7ディーラー表示: " + table.dealerHand.get(0).label() + " + ?"
        )));
        inv.setItem(10, icon(Material.LIME_STAINED_GLASS_PANE, "§aHIT", List.of("§7カードを1枚引きます。")));
        inv.setItem(12, icon(Material.RED_STAINED_GLASS_PANE, "§cSTAND", List.of("§7この手札で勝負します。")));
        inv.setItem(14, icon(Material.GOLD_INGOT, "§6DOUBLE DOWN", List.of(
                "§7掛け金と同額を手持ちから追加し、",
                "§7カードを1枚引いて即スタンドします。"
        )));
        inv.setItem(16, icon(Material.ENDER_EYE, "§dSWITCH", List.of(
                "§7Aを含む手札で、Aを11扱い/1扱いに切り替えます。"
        )));
        inv.setItem(22, icon(Material.BARRIER, "§7SURRENDER", List.of("§7掛け金の半分を戻して降ります。")));
        player.openInventory(inv);
    }

    private void hit(Player player) {
        PlayerState ps = table.players.get(player.getUniqueId());
        if (score(ps) >= 21) {
            player.sendMessage("§e21以上なのでこれ以上引けません。");
            ps.stood = true;
            checkEnd();
            return;
        }
        giveCard(player, ps, draw());
        int score = score(ps);
        if (score > 21) {
            ps.busted = true;
            ps.stood = true;
            player.sendMessage("§cバースト！ 合計: " + score);
        } else if (score == 21) {
            ps.stood = true;
            player.sendMessage("§e21です。これ以上カードは引けません。");
        }
        updateNameDisplays();
        if (!checkEnd()) openAction(player);
    }

    private void stand(Player player) {
        PlayerState ps = table.players.get(player.getUniqueId());
        ps.stood = true;
        player.sendMessage("§eスタンドしました。");
        updateNameDisplays();
        checkEnd();
    }

    private void doubleDown(Player player) {
        PlayerState ps = table.players.get(player.getUniqueId());
        if (ps.hand.size() != 2) {
            player.sendMessage("§cダブルダウンは最初の2枚の状態でのみ可能です。");
            return;
        }
        if (score(ps) >= 21) {
            player.sendMessage("§c21以上ではダブルダウンできません。");
            return;
        }
        if (countHandCoins(player) < ps.bet) {
            player.sendMessage("§cダブルダウンに必要な手持ちビーストコインが足りません（必要: " + ps.bet + "個）。");
            return;
        }
        removeCoins(player, ps.bet);
        ps.bet *= 2;
        ps.doubled = true;
        giveCard(player, ps, draw());
        int score = score(ps);
        ps.stood = true;
        if (score > 21) {
            ps.busted = true;
            player.sendMessage("§cダブルダウンでバースト！ 合計: " + score);
        } else {
            player.sendMessage("§6ダブルダウンしました。合計: " + score);
        }
        updateNameDisplays();
        checkEnd();
    }

    private void switchAceMode(Player player) {
        PlayerState ps = table.players.get(player.getUniqueId());
        boolean hasAce = ps.hand.stream().anyMatch(c -> c.rank == Rank.ACE);
        if (!hasAce) {
            player.sendMessage("§cAがないためスイッチできません。");
            return;
        }
        ps.aceHigh = !ps.aceHigh;
        int score = score(ps);
        player.sendMessage("§dAの扱いを " + (ps.aceHigh ? "11優先" : "1固定") + " にしました。合計: " + score);
        if (score > 21) {
            ps.busted = true;
            ps.stood = true;
            player.sendMessage("§cスイッチ後にバーストしました。");
        } else if (score == 21) {
            ps.stood = true;
        }
        updateNameDisplays();
        if (!checkEnd()) openAction(player);
    }

    private void surrender(Player player) {
        PlayerState ps = table.players.get(player.getUniqueId());
        int refund = ps.bet / 2;
        if (refund > 0) addCoins(player, refund);
        ps.bet = 0;
        ps.surrendered = true;
        ps.stood = true;
        player.sendMessage("§7サレンダーしました。返却: §b" + refund + "個");
        updateNameDisplays();
        checkEnd();
    }

    private boolean checkEnd() {
        if (table == null || table.phase != Phase.PLAYING) return false;
        boolean allDone = table.players.values().stream().allMatch(PlayerState::isDone);
        if (!allDone) return false;
        dealerPlay();
        settle();
        return true;
    }

    private void dealerPlay() {
        int score = dealerScore();
        while (score < 21) {
            boolean draw;
            if (score <= 15) {
                draw = true;
            } else {
                int chance = switch (score) {
                    case 16 -> 60;
                    case 17 -> 20;
                    case 18 -> 10;
                    case 19 -> 2;
                    case 20 -> 0;
                    default -> 0;
                };
                draw = ThreadLocalRandom.current().nextInt(100) < chance;
            }
            if (!draw) break;
            table.dealerHand.add(draw());
            score = dealerScore();
        }
    }

    private void settle() {
        int dealer = dealerScore();
        boolean dealerBust = dealer > 21;
        String dealerCards = cardsText(table.dealerHand);
        broadcastTable("§dディーラー手札: §f" + dealerCards + " §7合計: §e" + dealer + (dealerBust ? " §cBUST" : ""));

        for (UUID id : new ArrayList<>(table.players.keySet())) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = table.players.get(id);
            if (p == null || ps == null) continue;
            int playerScore = score(ps);
            int payout = 0;
            String result;
            if (ps.surrendered) {
                result = "§7サレンダー";
            } else if (ps.busted) {
                result = "§c負け（バースト）";
            } else if (dealerBust || playerScore > dealer) {
                int multiplier = 2;
                if (ps.doubled && playerScore == 21) {
                    multiplier = 5;
                    payout = ps.baseBet * multiplier;
                } else if (playerScore == 21) {
                    multiplier = 3;
                    payout = ps.bet * multiplier;
                } else {
                    payout = ps.bet * multiplier;
                }
                addCoins(p, payout);
                result = "§a勝ち §7配当: §b" + payout + "個 §7(" + multiplier + "倍)";
            } else if (playerScore == dealer) {
                payout = ps.bet;
                addCoins(p, payout);
                result = "§e引き分け §7返却: §b" + payout + "個";
            } else {
                result = "§c負け";
            }
            p.sendMessage("§d[Blackjack] §fあなた: §e" + playerScore + " §7/ ディーラー: §e" + dealer + " §f=> " + result);
            removePlayerCards(p, ps);
            restoreName(p, ps);
            p.closeInventory();
        }

        Entity dealerEntity = getDealerEntity();
        if (dealerEntity != null) {
            dealerEntity.setCustomName("§6Blackjack Dealer");
            dealerEntity.setCustomNameVisible(true);
        }
        table.phase = Phase.FINISHED;
        table = null;
    }

    private void giveCard(Player player, PlayerState ps, Card card) {
        ps.hand.add(card);
        ItemStack item = cardItem(player.getUniqueId(), card);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        } else {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack it = player.getInventory().getItem(i);
                if (isOwnedCard(it, player.getUniqueId(), card) && !ps.cardSlots.contains(i)) {
                    ps.cardSlots.add(i);
                    break;
                }
            }
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.1f);
    }

    private ItemStack cardItem(UUID owner, Card card) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§f§l" + card.label());
        meta.setLore(List.of(
                "§7Blackjack Card",
                "§7点数: §e" + card.rank.valueText(),
                "§8ゲーム中は移動/ドロップ不可"
        ));
        meta.setCustomModelData(card.rank.ordinal() + 1);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cardKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        pdc.set(rankKey, PersistentDataType.STRING, card.rank.name());
        it.setItemMeta(meta);
        return it;
    }

    private boolean isOwnedCard(ItemStack item, UUID owner, Card card) {
        if (!isBlackjackCard(item)) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return owner.toString().equals(pdc.get(ownerKey, PersistentDataType.STRING))
                && card.rank.name().equals(pdc.get(rankKey, PersistentDataType.STRING));
    }

    private boolean isBlackjackCard(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(cardKey, PersistentDataType.BYTE);
    }

    private void removePlayerCards(Player player, PlayerState ps) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isBlackjackCard(item)) {
                item.setAmount(0);
            }
        }
        ps.cardSlots.clear();
        ps.hand.clear();
    }

    private int score(PlayerState ps) {
        return score(ps.hand, ps.aceHigh);
    }

    private int dealerScore() {
        return score(table.dealerHand, true);
    }

    private int score(List<Card> cards, boolean aceHigh) {
        int total = 0;
        int aces = 0;
        for (Card c : cards) {
            if (c.rank == Rank.ACE) {
                aces++;
                total += aceHigh ? 11 : 1;
            } else {
                total += c.rank.points;
            }
        }
        if (aceHigh) {
            while (total > 21 && aces > 0) {
                total -= 10;
                aces--;
            }
        }
        return total;
    }

    private void updateNameDisplays() {
        if (table == null) return;
        for (UUID id : table.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = table.players.get(id);
            if (p == null || ps == null) continue;
            String suffix = table.phase == Phase.PLAYING
                    ? " §7[" + cardsText(ps.hand) + " §e" + score(ps) + "§7 / bet §b" + ps.bet + "§7]"
                    : " §7[bet §b" + ps.bet + "§7]";
            p.setCustomName("§f" + p.getName() + suffix);
            p.setCustomNameVisible(true);
        }
        Entity dealer = getDealerEntity();
        if (dealer != null && table.phase == Phase.PLAYING && !table.dealerHand.isEmpty()) {
            dealer.setCustomName("§6Dealer §7[" + table.dealerHand.get(0).label() + " + ?]");
            dealer.setCustomNameVisible(true);
        }
    }

    private void rememberName(Player player) {
        PlayerState ps = table.players.get(player.getUniqueId());
        if (ps == null || ps.nameRemembered) return;
        ps.originalCustomName = player.getCustomName();
        ps.originalCustomNameVisible = player.isCustomNameVisible();
        ps.nameRemembered = true;
    }

    private void restoreName(Player player, PlayerState ps) {
        if (ps.nameRemembered) {
            player.setCustomName(ps.originalCustomName);
            player.setCustomNameVisible(ps.originalCustomNameVisible);
        }
    }

    private void startHud() {
        hudTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (table == null) return;
                for (UUID id : table.players.keySet()) {
                    Player p = Bukkit.getPlayer(id);
                    PlayerState ps = table.players.get(id);
                    if (p == null || ps == null) continue;
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText("§eBlackjack §7| 掛け金: §b" + ps.bet
                                    + " §7| 手札: §f" + cardsText(ps.hand)
                                    + (table.phase == Phase.PLAYING ? " §7合計: §e" + score(ps) : "")));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void leaveTable(Player player, boolean notify) {
        if (table == null) return;
        PlayerState ps = table.players.remove(player.getUniqueId());
        if (ps == null) return;
        if (ps.bet > 0 && table.phase == Phase.LOBBY) {
            addCoins(player, ps.bet);
        }
        removePlayerCards(player, ps);
        restoreName(player, ps);
        player.closeInventory();
        if (notify) player.sendMessage("§7ブラックジャックから退出しました。");
        if (table.players.isEmpty()) {
            table = null;
            return;
        }
        if (player.getUniqueId().equals(table.host)) {
            table.host = table.players.keySet().iterator().next();
            Player newHost = Bukkit.getPlayer(table.host);
            if (newHost != null) newHost.sendMessage("§eあなたがブラックジャックの新しいホストです。");
        }
        if (table.phase == Phase.PLAYING) {
            checkEnd();
        } else {
            updateNameDisplays();
        }
    }

    private void endTable(String message) {
        if (table == null) return;
        for (UUID id : new ArrayList<>(table.players.keySet())) {
            Player p = Bukkit.getPlayer(id);
            PlayerState ps = table.players.get(id);
            if (p == null || ps == null) continue;
            if (ps.bet > 0 && table.phase == Phase.LOBBY) addCoins(p, ps.bet);
            removePlayerCards(p, ps);
            restoreName(p, ps);
            p.sendMessage(message);
            p.closeInventory();
        }
        table = null;
    }

    private boolean isDealer(Villager villager) {
        String configured = plugin.getConfig().getString("blackjack.dealer.uuid", "");
        if (configured != null && !configured.isBlank()) {
            try {
                if (villager.getUniqueId().equals(UUID.fromString(configured))) return true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        String name = villager.getCustomName();
        if (name == null) return false;
        String plain = org.bukkit.ChatColor.stripColor(name);
        if (plain == null) return false;
        String lower = plain.toLowerCase(Locale.ROOT);
        return lower.contains("blackjack") || lower.contains("ブラックジャック") || lower.contains("ディーラー");
    }

    private Entity findDealerFor(Player player) {
        if (table != null) return getDealerEntity();
        FileConfiguration cfg = plugin.getConfig();
        String configured = cfg.getString("blackjack.dealer.uuid", "");
        if (configured != null && !configured.isBlank()) {
            try {
                UUID uuid = UUID.fromString(configured);
                for (Entity e : player.getWorld().getEntities()) {
                    if (e.getUniqueId().equals(uuid)) return e;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Entity e : player.getNearbyEntities(8, 4, 8)) {
            if (e instanceof Villager v && isDealer(v)) return v;
        }
        return null;
    }

    private Entity getDealerEntity() {
        if (table == null) return null;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getUniqueId().equals(table.dealerId)) return e;
            }
        }
        return null;
    }

    private boolean isTopClick(InventoryClickEvent event) {
        return event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory());
    }

    private int countHandCoins(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) total += item.getAmount();
        }
        return total;
    }

    private void removeCoins(Player player, int amount) {
        int remaining = amount;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != Material.DIAMOND) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) inv.setItem(i, null);
            remaining -= take;
        }
    }

    private void addCoins(Player player, int amount) {
        if (amount <= 0) return;
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(64, remaining);
            ItemStack stack = new ItemStack(Material.DIAMOND, stackAmount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            remaining -= stackAmount;
        }
    }

    private int freeSlots(Player player) {
        int n = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) n++;
        }
        return n;
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private String cardsText(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "-";
        List<String> labels = new ArrayList<>();
        for (Card c : cards) labels.add(c.label());
        return String.join(" ", labels);
    }

    private void broadcastLobby(String msg) {
        if (table == null) return;
        for (UUID id : table.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void broadcastTable(String msg) {
        if (table == null) return;
        for (UUID id : table.players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(msg);
        }
    }

    private enum Phase { LOBBY, PLAYING, FINISHED }

    private static final class Table {
        private UUID host;
        private final UUID dealerId;
        private Phase phase = Phase.LOBBY;
        private final Map<UUID, PlayerState> players = new HashMap<>();
        private List<Card> deck = new ArrayList<>();
        private final List<Card> dealerHand = new ArrayList<>();

        private Table(UUID host, UUID dealerId) {
            this.host = host;
            this.dealerId = dealerId;
        }
    }

    private static final class PlayerState {
        private int bet;
        private int baseBet;
        private boolean stood;
        private boolean busted;
        private boolean doubled;
        private boolean surrendered;
        private boolean aceHigh = true;
        private final List<Card> hand = new ArrayList<>();
        private final Set<Integer> cardSlots = new HashSet<>();
        private boolean nameRemembered;
        private String originalCustomName;
        private boolean originalCustomNameVisible;

        private PlayerState(Player player) {
            this.originalCustomName = player.getCustomName();
            this.originalCustomNameVisible = player.isCustomNameVisible();
            this.nameRemembered = true;
        }

        private boolean isDone() {
            return stood || busted || surrendered;
        }
    }

    private record Card(Rank rank) {
        private String label() {
            return rank.label;
        }
    }

    private enum Rank {
        ACE("A", 11),
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 8),
        NINE("9", 9),
        TEN("10", 10),
        JACK("J", 10),
        QUEEN("Q", 10),
        KING("K", 10);

        private final String label;
        private final int points;

        Rank(String label, int points) {
            this.label = label;
            this.points = points;
        }

        private String valueText() {
            return this == ACE ? "1/11" : String.valueOf(points);
        }
    }
}
