package me.bokan.perocasino;

import me.bokan.perocasino.commands.BalanceCommand;
import me.bokan.perocasino.commands.CasinoCommand;
import me.bokan.perocasino.commands.ChinchiroCommand;
import me.bokan.perocasino.commands.CommandBookCommand;
import me.bokan.perocasino.commands.DepositCommand;
import me.bokan.perocasino.commands.HiLoSelectCommand;
import me.bokan.perocasino.commands.PerocasinoCommand;
import me.bokan.perocasino.economy.EconomyManager;
import me.bokan.perocasino.games.blackjack.BlackjackService;
import me.bokan.perocasino.games.chinchiro.ChinchiroDiceService;
import me.bokan.perocasino.games.chinchiro.ChinchiroTableService;
import me.bokan.perocasino.games.hilo.HiLoService;
import me.bokan.perocasino.games.slot.SlotMachineService;
import me.bokan.perocasino.games.slotdisplay.SlotDisplayService;
import me.bokan.perocasino.listeners.CasinoMenuListener;
import me.bokan.perocasino.listeners.CommandBookListener;
import me.bokan.perocasino.listeners.CommandWandListener;
import me.bokan.perocasino.listeners.GameMenuListener;
import me.bokan.perocasino.listeners.LoanMenuListener;
import me.bokan.perocasino.listeners.NetherPortalTeleportListener;
import me.bokan.perocasino.listeners.QuarryRespawnListener;
import me.bokan.perocasino.listeners.RuleBookListener;
import me.bokan.perocasino.listeners.RouletteBetBoardMenuListener;
import me.bokan.perocasino.listeners.RouletteBetMenuListener;
import me.bokan.perocasino.listeners.RouletteInteractListener;
import me.bokan.perocasino.listeners.SlotDisplayBetDealerListener;
import me.bokan.perocasino.listeners.SlotDisplayBlockButtonListener;
import me.bokan.perocasino.listeners.SlotDisplayInteractListener;
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
    private BlackjackService blackjackService;
    private HiLoService hiLoService;
    private SlotDisplayService slotDisplayService;
    private ChinchiroDiceService chinchiroDiceService;
    private ChinchiroTableService chinchiroTableService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economyManager = new EconomyManager();

        getCommand("balance").setExecutor(new BalanceCommand(economyManager));
        getCommand("deposit").setExecutor(new DepositCommand(economyManager));
        getCommand("casino").setExecutor(new CasinoCommand());
        getCommand("commandbook").setExecutor(new CommandBookCommand(this));

        slotMachineService = new SlotMachineService(this, economyManager);
        blackjackService = new BlackjackService(this, economyManager);
        hiLoService = new HiLoService(this, economyManager);

        org.bukkit.command.PluginCommand hiloCmd = getCommand("hilo");
        if (hiloCmd != null) {
            hiloCmd.setExecutor(new HiLoSelectCommand(hiLoService));
        }

        slotDisplayService = new SlotDisplayService(this, economyManager);
        slotDisplayService.reloadFromConfig();

        chinchiroDiceService = new ChinchiroDiceService(this);
        chinchiroDiceService.reloadFromConfig();
        chinchiroTableService = new ChinchiroTableService(this, economyManager, chinchiroDiceService);
        org.bukkit.command.PluginCommand chinchiroCmd = getCommand("chinchiro");
        if (chinchiroCmd != null) {
            chinchiroCmd.setExecutor(new ChinchiroCommand(this, chinchiroDiceService));
        } else {
            getLogger().severe("plugin.yml に chinchiro コマンドが定義されていません。");
        }

        rouletteDisplayService = new RouletteDisplayService(this);
        rouletteDisplayService.reloadFromConfig();

        LoanMenuListener loanListener = new LoanMenuListener(economyManager, this);
        getServer().getPluginManager().registerEvents(loanListener, this);
        getServer().getPluginManager().registerEvents(
                new CasinoMenuListener(loanListener, this, slotMachineService, blackjackService, hiLoService,
                        chinchiroTableService), this);

        getServer().getPluginManager().registerEvents(new WalletListener(economyManager, this), this);
        getServer().getPluginManager().registerEvents(new RuleBookListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBookListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandWandListener(this), this);

        RouletteBetMenuListener betListener = new RouletteBetMenuListener(this);
        getServer().getPluginManager().registerEvents(betListener, this);
        RouletteBetBoardService betBoardService = new RouletteBetBoardService(this, economyManager);
        getServer().getPluginManager().registerEvents(new RouletteBetBoardMenuListener(betBoardService), this);
        getServer().getPluginManager().registerEvents(new RouletteInteractListener(betListener, betBoardService), this);

        rouletteHubService = new RouletteHubService(this, economyManager, betListener, rouletteDisplayService, betBoardService);
        rouletteHubService.runTaskTimer(this, 0L, 1L);

        org.bukkit.command.PluginCommand pc = getCommand("perocasino");
        if (pc != null) {
            PerocasinoCommand adminCmd = new PerocasinoCommand(this, () -> {
                if (rouletteHubService != null) rouletteHubService.reloadFromConfig();
                if (slotMachineService != null) slotMachineService.reloadFromConfig();
                if (slotDisplayService != null) slotDisplayService.reloadFromConfig();
                if (chinchiroDiceService != null) chinchiroDiceService.reloadFromConfig();
                if (chinchiroTableService != null) chinchiroTableService.reloadFromConfig();
            }, slotDisplayService, chinchiroDiceService);
            pc.setExecutor(adminCmd);
            pc.setTabCompleter(adminCmd);
        }

        getServer().getPluginManager().registerEvents(new QuarryRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new SlotInteractListener(slotMachineService), this);
        getServer().getPluginManager().registerEvents(new SlotDisplayInteractListener(slotDisplayService), this);
        getServer().getPluginManager().registerEvents(new SlotDisplayBlockButtonListener(slotDisplayService), this);
        getServer().getPluginManager().registerEvents(new SlotDisplayBetDealerListener(this, economyManager), this);
        getServer().getPluginManager().registerEvents(new SlotMenuListener(), this);
        getServer().getPluginManager().registerEvents(new SlotSessionCleanupListener(slotMachineService), this);
        getServer().getPluginManager().registerEvents(new GameMenuListener(), this);
        getServer().getPluginManager().registerEvents(new NetherPortalTeleportListener(this), this);
        getServer().getPluginManager().registerEvents(blackjackService, this);
        getServer().getPluginManager().registerEvents(hiLoService, this);
        getServer().getPluginManager().registerEvents(chinchiroTableService, this);

        new HudTask(economyManager).runTaskTimer(this, 0L, 20L);
        new LoanTask(economyManager).runTaskTimer(this, 20L, 20L);

        getLogger().info("PeRoCasino が有効化されました！");
    }

    @Override
    public void onDisable() {
        if (rouletteHubService != null) {
            rouletteHubService.shutdown();
        }
        if (blackjackService != null) {
            blackjackService.shutdown();
        }
        if (hiLoService != null) {
            hiLoService.shutdown();
        }
        if (slotDisplayService != null) {
            slotDisplayService.shutdown();
        }
        if (chinchiroDiceService != null) {
            chinchiroDiceService.removeAllDisplays();
        }
        if (chinchiroTableService != null) {
            chinchiroTableService.shutdown();
        }
        getLogger().info("PeRoCasino が無効化されました。");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public BlackjackService getBlackjackService() {
        return blackjackService;
    }

    public HiLoService getHiLoService() {
        return hiLoService;
    }

    public ChinchiroDiceService getChinchiroDiceService() {
        return chinchiroDiceService;
    }

    public ChinchiroTableService getChinchiroTableService() {
        return chinchiroTableService;
    }

    public RouletteDisplayService getRouletteDisplayService() {
        return rouletteDisplayService;
    }
}
