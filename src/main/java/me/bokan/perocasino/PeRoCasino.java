package me.bokan.perocasino;

import me.bokan.perocasino.commands.BalanceCommand;
import me.bokan.perocasino.commands.CasinoCommand;
import me.bokan.perocasino.commands.DepositCommand;
import me.bokan.perocasino.commands.PerocasinoCommand;
import me.bokan.perocasino.economy.EconomyManager;
import me.bokan.perocasino.games.slot.SlotMachineService;
import me.bokan.perocasino.listeners.CasinoMenuListener;
import me.bokan.perocasino.listeners.GameMenuListener;
import me.bokan.perocasino.listeners.LoanMenuListener;
import me.bokan.perocasino.listeners.CommandBookListener;
import me.bokan.perocasino.listeners.NetherPortalTeleportListener;
import me.bokan.perocasino.listeners.QuarryRespawnListener;
import me.bokan.perocasino.listeners.RuleBookListener;
import me.bokan.perocasino.listeners.RouletteBetMenuListener;
import me.bokan.perocasino.listeners.RouletteInteractListener;
import me.bokan.perocasino.listeners.SlotInteractListener;
import me.bokan.perocasino.listeners.SlotMenuListener;
import me.bokan.perocasino.listeners.SlotSessionCleanupListener;
import me.bokan.perocasino.listeners.WalletListener;
import me.bokan.perocasino.roulette.RouletteBetBoardService;
import me.bokan.perocasino.roulette.RouletteDisplayService;
import me.bokan.perocasino.roulette.RouletteHubService;
import me.bokan.perocasino.tasks.HudTask;
import me.bokan.perocasino.tasks.LoanTask;
import org.bukkit.plugin.java.JavaPlugin;

public class PeRoCasino extends JavaPlugin {

    private EconomyManager economyManager;
    private RouletteHubService rouletteHubService;
    private SlotMachineService slotMachineService;
    private RouletteDisplayService rouletteDisplayService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economyManager = new EconomyManager();

        getCommand("balance").setExecutor(new BalanceCommand(economyManager));
        getCommand("deposit").setExecutor(new DepositCommand(economyManager));
        getCommand("casino").setExecutor(new CasinoCommand());

        slotMachineService = new SlotMachineService(this, economyManager);

        // ルーレット表示（ItemDisplay）
        RouletteDisplayService rouletteDisplayService = new RouletteDisplayService(this);
        rouletteDisplayService.reloadFromConfig();

        // LOAN GUI リスナー → カジノメインリスナーへ渡す
        LoanMenuListener loanListener = new LoanMenuListener(economyManager, this);
        getServer().getPluginManager().registerEvents(loanListener, this);
        getServer().getPluginManager().registerEvents(new CasinoMenuListener(loanListener, this, slotMachineService), this);

        // 財布システム（スロット8: 引き出し口 / スロット35: 専用バンドル）
        getServer().getPluginManager().registerEvents(new WalletListener(economyManager, this), this);

        // ルールブック（ホットバー左端0に固定）
        getServer().getPluginManager().registerEvents(new RuleBookListener(this), this);

        // 初回参加者向け: コマンド集ルールブック（固定なし）
        getServer().getPluginManager().registerEvents(new CommandBookListener(), this);

        // 【追加】ルーレットのリスナーを登録
        RouletteBetMenuListener betListener = new RouletteBetMenuListener(this);
        getServer().getPluginManager().registerEvents(betListener, this);
        RouletteBetBoardService betBoardService = new RouletteBetBoardService(this, economyManager);
        getServer().getPluginManager().registerEvents(new RouletteInteractListener(betListener, betBoardService), this);

        rouletteDisplayService = new RouletteDisplayService(this);
        rouletteHubService = new RouletteHubService(this, economyManager, betListener, rouletteDisplayService, betBoardService);
        rouletteHubService.runTaskTimer(this, 0L, 1L);

        // 初回参加者へコマンド集ブック配布（固定なし）
        getServer().getPluginManager().registerEvents(new CommandBookListener(), this);

        org.bukkit.command.PluginCommand pc = getCommand("perocasino");
        if (pc != null) {
            PerocasinoCommand adminCmd = new PerocasinoCommand(this, () -> {
                if (rouletteHubService != null) rouletteHubService.reloadFromConfig();
                if (slotMachineService != null) slotMachineService.reloadFromConfig();
            });
            pc.setExecutor(adminCmd);
            pc.setTabCompleter(adminCmd);
        }

        getServer().getPluginManager().registerEvents(new QuarryRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new SlotInteractListener(slotMachineService), this);
        getServer().getPluginManager().registerEvents(new SlotMenuListener(), this);
        getServer().getPluginManager().registerEvents(new SlotSessionCleanupListener(slotMachineService), this);
        getServer().getPluginManager().registerEvents(new GameMenuListener(), this);
        getServer().getPluginManager().registerEvents(new NetherPortalTeleportListener(this), this);

        // HUD 表示（1秒ごと）
        new HudTask(economyManager).runTaskTimer(this, 0L, 20L);

        // 利息タスク（1秒ごとにオンラインプレイヤーの借金をチェック）
        new LoanTask(economyManager).runTaskTimer(this, 20L, 20L);

        getLogger().info("PeRoCasino が有効化されました！");
    }

    @Override
    public void onDisable() {
        if (rouletteHubService != null) {
            rouletteHubService.shutdown();
        }
        getLogger().info("PeRoCasino が無効化されました。");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}