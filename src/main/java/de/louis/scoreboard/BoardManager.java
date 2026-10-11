package de.louis.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BoardManager implements Listener {

    private static final Statistic PLAYTIME_STAT = findPlaytimeStat();

    private final LouisScoreboard plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private final Set<UUID> warned = new HashSet<>();

    private BukkitTask task;
    private Component title = Component.empty();
    private List<String> lines = List.of();
    private String currency = "";

    public BoardManager(LouisScoreboard plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin.owner());
        reload();
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Player p : Bukkit.getOnlinePlayers()) reset(p);
        boards.clear();
    }

    public void reload() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) reset(p);
        boards.clear();

        FileConfiguration cfg = plugin.getConfig();
        title = mm.deserialize(cfg.getString("title", "<gold><bold>SERVER"));
        currency = cfg.getString("currency-symbol", "");
        List<String> l = cfg.getStringList("lines");
        lines = l.size() > 15 ? l.subList(0, 15) : l;
        long interval = Math.max(5L, cfg.getLong("update-interval-ticks", 20L));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!disabled.contains(p.getUniqueId())) create(p);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin.owner(), this::updateAll, 20L, interval);
    }

    /** @return true, wenn das Scoreboard jetzt eingeschaltet ist */
    public boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        if (disabled.remove(id)) {
            create(p);
            return true;
        }
        disabled.add(id);
        reset(p);
        boards.remove(id);
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin.owner(), () -> {
            if (p.isOnline() && !disabled.contains(p.getUniqueId()) && !boards.containsKey(p.getUniqueId())) {
                create(p);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }

    private void create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("louis", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank()); // rote Zahlen rechts ausblenden

        for (int i = 0; i < lines.size(); i++) {
            Team team = sb.registerNewTeam("l" + i);
            String entry = entry(i);
            team.addEntry(entry);
            obj.getScore(entry).setScore(lines.size() - i);
        }
        p.setScoreboard(sb);
        boards.put(p.getUniqueId(), sb);
        update(p);
    }

    private void reset(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                update(p);
            } catch (Exception ex) {
                plugin.getLogger().warning("Fehler beim Aktualisieren für " + p.getName() + ": " + ex);
            }
        }
    }

    private void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;

        if (p.getScoreboard() != sb) {
            if (warned.add(p.getUniqueId())) {
                plugin.getLogger().warning("Das Scoreboard von " + p.getName()
                        + " wurde von einem anderen Plugin ersetzt - ich setze es zurück. "
                        + "Bitte prüfe, welches andere Plugin ein Scoreboard anzeigt.");
            }
            p.setScoreboard(sb);
        }

        double hours = PLAYTIME_STAT == null ? 0.0 : p.getStatistic(PLAYTIME_STAT) / 20.0 / 3600.0;
        String money = String.format(Locale.GERMANY, "%,.2f", plugin.getMoney(p))
                + (currency.isEmpty() ? "" : " " + currency);
        String bank = String.format(Locale.GERMANY, "%,.2f", plugin.getBank(p))
                + (currency.isEmpty() ? "" : " " + currency);

        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("money", money),
                Placeholder.unparsed("bank", bank),
                Placeholder.parsed("rank", plugin.getRankTag(p)),
                Placeholder.unparsed("playtime", String.format(Locale.GERMANY, "%.1f", hours)),
                Placeholder.unparsed("kills", String.valueOf(p.getStatistic(Statistic.PLAYER_KILLS))),
                Placeholder.unparsed("deaths", String.valueOf(p.getStatistic(Statistic.DEATHS)))
        );

        for (int i = 0; i < lines.size(); i++) {
            Team team = sb.getTeam("l" + i);
            if (team != null) team.prefix(mm.deserialize(lines.get(i), resolver));
        }
    }

    /** Unsichtbarer, eindeutiger Eintrag pro Zeile (§0§r, §1§r, ...). */
    private static String entry(int i) {
        return "\u00a7" + Character.forDigit(i, 16) + "\u00a7r";
    }

    private static Statistic findPlaytimeStat() {
        for (String name : new String[]{"PLAY_TIME", "PLAY_ONE_MINUTE"}) {
            try {
                return Statistic.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
