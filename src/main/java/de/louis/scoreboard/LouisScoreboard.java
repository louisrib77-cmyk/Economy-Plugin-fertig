package de.louis.scoreboard;

import de.louis.economy.LouisEconomy;
import de.louis.staat.StaatsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

/** Scoreboard (Seitenleiste) mit Geld und Bankkonto. Config: scoreboard.yml */
public class LouisScoreboard implements CommandExecutor {

    private final LouisEconomy owner;
    private final StaatsPlugin staat;
    private YamlConfiguration config = new YamlConfiguration();
    private BoardManager boards;

    public LouisScoreboard(LouisEconomy owner, StaatsPlugin staat) {
        this.owner = owner;
        this.staat = staat;
    }

    public void enable() {
        loadConfig();
        boards = new BoardManager(this);
        boards.start();
        var cmd = owner.getCommand("sb");
        if (cmd != null) cmd.setExecutor(this);
    }

    public void disable() {
        if (boards != null) boards.stop();
    }

    public void reload() {
        loadConfig();
        if (boards != null) boards.reload();
    }

    private void loadConfig() {
        File file = new File(owner.getDataFolder(), "scoreboard.yml");
        if (!file.exists()) {
            owner.getDataFolder().mkdirs();
            owner.saveResource("scoreboard.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    FileConfiguration getConfig() {
        return config;
    }

    Logger getLogger() {
        return owner.getLogger();
    }

    LouisEconomy owner() {
        return owner;
    }

    /** Bargeld (aus dem JobsPlugin). */
    double getMoney(Player player) {
        try {
            return owner.bridge().getMoney(player.getUniqueId());
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    /** Guthaben auf dem Bankkonto (StaatsSystem). */
    double getBank(Player player) {
        return staat.bankBalance(player.getUniqueId());
    }

    /** Rang mit Farbe, z. B. "<gold>Kanzler". */
    String getRankTag(Player player) {
        UUID id = player.getUniqueId();
        return staat.rankTag(id);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("louisscoreboard.reload")) {
                sender.sendMessage("Dafür hast du keine Rechte.");
                return true;
            }
            reload();
            sender.sendMessage("Scoreboard neu geladen.");
            return true;
        }
        if (sender instanceof Player p) {
            boolean on = boards.toggle(p);
            p.sendMessage(on ? "Scoreboard eingeschaltet." : "Scoreboard ausgeschaltet.");
        } else {
            sender.sendMessage("Nur für Spieler.");
        }
        return true;
    }
}
