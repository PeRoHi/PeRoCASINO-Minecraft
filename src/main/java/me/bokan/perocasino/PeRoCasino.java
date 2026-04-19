package me.bokan.perocasino;

import me.bokan.perocasino.commands.BalanceCommand;
import me.bokan.perocasino.commands.CasinoCommand;
import me.bokan.perocasino.commands.DepositCommand;
import me.bokan.perocasino.economy.EconomyManager;
import me.bokan.perocasino.listeners.CasinoMenuListener;
import me.bokan.perocasino.listeners.LoanMenuListener;
import me.bokan.perocasino.listeners.RouletteBetMenuListener;
import me.bokan.perocasino.listeners.RouletteInteractListener;
import me.bokan.perocasino.listeners.WalletListener;
import me.bokan.perocasino.tasks.HudTask;
import me.bokan.perocasino.tasks.LoanTask;
import org.bukkit.plugin.java.JavaPlugin;

public class PeRoCasino extends JavaPlugin {

    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        economyManager = new EconomyManager();

        getCommand("balance").setExecutor(new BalanceCommand(economyManager));
        getCommand("deposit").setExecutor(new DepositCommand(economyManager));
        getCommand("casino").setExecutor(new CasinoCommand());

        // LOAN GUI リスナー → カジノメインリスナーへ渡す
        LoanMenuListener loanListener = new LoanMenuListener(economyManager, this);
        getServer().getPluginManager().registerEvents(loanListener, this);
        getServer().getPluginManager().registerEvents(new CasinoMenuListener(loanListener, this), this);

        // 財布システム（スロット8: 引き出し口 / スロット35: 専用バンドル）
        getServer().getPluginManager().registerEvents(new WalletListener(economyManager, this), this);

        // 【追加】ルーレットのリスナーを登録
        RouletteBetMenuListener betListener = new RouletteBetMenuListener();
        getServer().getPluginManager().registerEvents(betListener, this);
        getServer().getPluginManager().registerEvents(new RouletteInteractListener(betListener), this);

        // HUD 表示（1秒ごと）
        new HudTask(economyManager).runTaskTimer(this, 0L, 20L);

        // 利息タスク（1秒ごとにオンラインプレイヤーの借金をチェック）
        new LoanTask(economyManager).runTaskTimer(this, 20L, 20L);

        getLogger().info("PeRoCasino が有効化されました！");
    }

    @Override
    public void onDisable() {
        getLogger().info("PeRoCasino が無効化されました。");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}