package de.louis.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /sellall: Menü, das alles zeigt, was du im Inventar hast und verkaufen kannst.
 * Klick auf ein Item verkauft alles davon, der grüne Block verkauft alles auf einmal.
 * Preise kommen aus prices.yml (wie bei /sell).
 */
public class SellAllManager implements Listener, CommandExecutor {

    private static final int SIZE = 54;
    private static final int ITEM_SLOTS = 45;
    private static final int INFO_SLOT = 45;
    private static final int SELL_ALL_SLOT = 49;

    private record Group(Material material, int count, double unit, double value) {}

    private static final class SellAllHolder implements InventoryHolder {
        private Inventory inventory;
        private List<Material> entries = List.of();

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final LouisEconomy plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration config = new YamlConfiguration();

    public SellAllManager(LouisEconomy plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "sellgui.yml");
        if (!file.exists()) plugin.saveResource("sellgui.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    // --------------------------------------------------------- Nachrichten

    private Component msg(String key, TagResolver... resolvers) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String text = config.getString("messages." + key, "<red>Fehlende Nachricht: " + key);
        return mm.deserialize(prefix + text, resolvers);
    }

    private Component raw(String key, TagResolver... resolvers) {
        String text = config.getString("messages." + key, "<red>Fehlende Nachricht: " + key);
        return mm.deserialize(text, resolvers);
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private int firstSlot() {
        return config.getBoolean("include-hotbar", true) ? 0 : 9;
    }

    // ------------------------------------------------------------- Befehl

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("only-players"));
            return true;
        }
        if (collect(p).isEmpty()) {
            p.sendMessage(msg("nothing-to-sell"));
            return true;
        }
        SellAllHolder holder = new SellAllHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, raw("title"));
        holder.inventory = inv;
        render(holder, p);
        p.openInventory(inv);
        return true;
    }

    // --------------------------------------------------------------- Daten

    /** Alles Verkaufbare im Inventar, nach Material zusammengefasst (wertvollstes zuerst). */
    private List<Group> collect(Player p) {
        SellManager sell = plugin.sell();
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        Map<Material, Double> values = new EnumMap<>(Material.class);

        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = firstSlot(); i < storage.length; i++) {
            ItemStack item = storage[i];
            if (item == null || item.getType().isAir()) continue;
            double value = sell.stackValue(item);
            if (value <= 0) continue;
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
            values.merge(item.getType(), value, Double::sum);
        }

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : counts.entrySet()) {
            Material m = e.getKey();
            int count = e.getValue();
            double value = values.get(m);
            groups.add(new Group(m, count, count == 0 ? 0.0 : value / count, value));
        }
        groups.sort(Comparator.comparingDouble(Group::value).reversed());
        return groups;
    }

    // ---------------------------------------------------------------- Menü

    private void render(SellAllHolder holder, Player p) {
        Inventory inv = holder.inventory;
        inv.clear();

        List<Group> groups = collect(p);
        holder.entries = new ArrayList<>();
        double total = 0.0;
        int totalCount = 0;

        for (Group g : groups) {
            total += g.value();
            totalCount += g.count();
        }

        for (int i = 0; i < groups.size() && i < ITEM_SLOTS; i++) {
            Group g = groups.get(i);
            holder.entries.add(g.material());

            ItemStack item = new ItemStack(g.material(), Math.max(1, Math.min(g.count(), g.material().getMaxStackSize())));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                for (String line : config.getStringList("messages.item-lore")) {
                    lore.add(noItalic(mm.deserialize(line,
                            Placeholder.unparsed("count", String.valueOf(g.count())),
                            Placeholder.unparsed("price", plugin.formatMoney(g.unit())),
                            Placeholder.unparsed("value", plugin.formatMoney(g.value())))));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = ITEM_SLOTS; i < SIZE; i++) inv.setItem(i, filler);

        // Info: Kontostand
        String balance;
        try {
            balance = plugin.formatMoney(plugin.bridge().getMoney(p.getUniqueId()));
        } catch (RuntimeException e) {
            balance = "?";
        }
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        im.displayName(noItalic(raw("info-name")));
        List<Component> infoLore = new ArrayList<>();
        for (String line : config.getStringList("messages.info-lore")) {
            infoLore.add(noItalic(mm.deserialize(line, Placeholder.unparsed("money", balance))));
        }
        im.lore(infoLore);
        info.setItemMeta(im);
        inv.setItem(INFO_SLOT, info);

        // Alles verkaufen
        ItemStack button = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta bm = button.getItemMeta();
        bm.displayName(noItalic(raw("sell-all-name")));
        List<Component> buttonLore = new ArrayList<>();
        for (String line : config.getStringList("messages.sell-all-lore")) {
            buttonLore.add(noItalic(mm.deserialize(line,
                    Placeholder.unparsed("count", String.valueOf(totalCount)),
                    Placeholder.unparsed("money", plugin.formatMoney(total)))));
        }
        bm.lore(buttonLore);
        button.setItemMeta(bm);
        inv.setItem(SELL_ALL_SLOT, button);
    }

    // ------------------------------------------------------------ Verkaufen

    /** Verkauft alles (only == null) oder nur ein Material. Gibt true zurück, wenn etwas verkauft wurde. */
    private boolean sell(Player p, Material only) {
        SellManager sell = plugin.sell();
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();

        List<Integer> slots = new ArrayList<>();
        double total = 0.0;
        int count = 0;
        for (int i = firstSlot(); i < storage.length; i++) {
            ItemStack item = storage[i];
            if (item == null || item.getType().isAir()) continue;
            if (only != null && item.getType() != only) continue;
            double value = sell.stackValue(item);
            if (value <= 0) continue;
            slots.add(i);
            total += value;
            count += item.getAmount();
        }
        if (slots.isEmpty()) return false;
        total = round(total);

        // Erst das Geld gutschreiben - klappt das nicht, bleiben alle Items im Inventar.
        try {
            JobsBridge bridge = plugin.bridge();
            double current = bridge.getMoney(p.getUniqueId());
            bridge.setMoney(p.getUniqueId(), round(current + total));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Verkauf (SellAll) fehlgeschlagen für " + p.getName() + ": " + e);
            p.sendMessage(msg("error"));
            return false;
        }

        for (int slot : slots) inv.setItem(slot, null);

        if (only == null) {
            p.sendMessage(msg("sold-all",
                    Placeholder.unparsed("count", String.valueOf(count)),
                    Placeholder.unparsed("money", plugin.formatMoney(total))));
        } else {
            p.sendMessage(msg("sold-one",
                    Placeholder.unparsed("count", String.valueOf(count)),
                    Placeholder.unparsed("item", pretty(only)),
                    Placeholder.unparsed("money", plugin.formatMoney(total))));
        }
        return true;
    }

    // -------------------------------------------------------------- Events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellAllHolder holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        if (slot == SELL_ALL_SLOT) {
            sell(p, null);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }
        if (slot < 0 || slot >= ITEM_SLOTS || slot >= holder.entries.size()) return;

        sell(p, holder.entries.get(slot));
        if (collect(p).isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        } else {
            render(holder, p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof SellAllHolder) {
            e.setCancelled(true);
        }
    }
}
