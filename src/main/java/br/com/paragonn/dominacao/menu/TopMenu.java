package br.com.paragonn.dominacao.menu;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.score.ClanStats;
import br.com.paragonn.dominacao.score.PlayerStats;
import br.com.paragonn.dominacao.score.ScoreManager;
import br.com.paragonn.dominacao.util.Heads;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Menu do NPC de top (menu.yml): cada cabeça é um ranking e a descrição mostra as primeiras posições.
 * Tipos: CLAN_POINTS, CLAN_DOMINATIONS, CLAN_KILLS, PLAYER_KILLS.
 */
public final class TopMenu implements Listener {

    private static final String FILE = "menu.yml";
    private static final int MAX_TITLE = 32; // limite de título de inventário na 1.8

    enum Ranking { CLAN_POINTS, CLAN_DOMINATIONS, CLAN_KILLS, PLAYER_KILLS }

    /** Marca o inventário como nosso e guarda os slots de "fechar". */
    private static final class Holder implements InventoryHolder {
        private final Set<Integer> closeSlots = new HashSet<Integer>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Uma linha de ranking já resolvida. */
    private static final class Entry {
        final String key;
        final String name;
        final long value;

        Entry(String key, String name, long value) {
            this.key = key;
            this.name = name;
            this.value = value;
        }
    }

    private final JavaPlugin plugin;
    private final ScoreManager scores;
    private final ClanManager clans;
    private ConfigurationSection menu;

    public TopMenu(JavaPlugin plugin, ScoreManager scores, ClanManager clans) {
        this.plugin = plugin;
        this.scores = scores;
        this.clans = clans;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao ler " + FILE + ", usando o menu padrao.", e);
        }
        menu = yaml.getConfigurationSection("top-menu");
        if (menu == null) {
            menu = defaults().getConfigurationSection("top-menu");
        }
    }

    public void open(Player player, Arena arena) {
        if (menu == null) {
            return;
        }
        Holder holder = new Holder();
        int rows = Math.max(1, Math.min(6, menu.getInt("rows", 4)));
        String title = Text.color(Text.replace(menu.getString("title", "&8TOP (%arena%)"), "arena", arena.displayName()));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title);
        holder.inventory = inventory;

        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item != null) {
                    place(inventory, item.getInt("slot", -1), rankingItem(player, arena, item));
                }
            }
        }
        ConfigurationSection close = menu.getConfigurationSection("close");
        if (close != null) {
            int slot = close.getInt("slot", -1);
            if (place(inventory, slot, simpleItem(close, arena))) {
                holder.closeSlots.add(slot);
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        Holder holder = (Holder) event.getView().getTopInventory().getHolder();
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()
                && holder.closeSlots.contains(event.getRawSlot())
                && event.getWhoClicked() instanceof Player) {
            final Player player = (Player) event.getWhoClicked();
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ itens

    private ItemStack rankingItem(Player viewer, Arena arena, ConfigurationSection s) {
        Ranking ranking = parseRanking(s.getString("type", "CLAN_POINTS"));
        List<Entry> entries = entries(arena, ranking);
        int size = Math.max(1, Math.min(15, menu.getInt("entries", 5)));

        List<String> lore = new ArrayList<String>();
        for (String line : s.getStringList("header")) {
            lore.add(Text.color(Text.replace(line, "arena", arena.displayName())));
        }
        for (int i = 0; i < size; i++) {
            if (i < entries.size()) {
                Entry entry = entries.get(i);
                lore.add(Text.color(Text.replace(s.getString("line", "&e%position%º &f%name% &7- &a%value%"),
                        "position", i + 1, "name", entry.name, "value", Text.number(entry.value))));
            } else {
                lore.add(Text.color(Text.replace(s.getString("empty-line", "&e%position%º &8---"), "position", i + 1)));
            }
        }
        String myKey = ranking == Ranking.PLAYER_KILLS ? viewer.getUniqueId().toString() : clans.clanKey(viewer);
        int myPosition = 0;
        long myValue = 0;
        for (int i = 0; myKey != null && i < entries.size(); i++) {
            if (entries.get(i).key.equals(myKey)) {
                myPosition = i + 1;
                myValue = entries.get(i).value;
                break;
            }
        }
        for (String line : s.getStringList("footer")) {
            lore.add(Text.color(Text.replace(line, "arena", arena.displayName(),
                    "my_position", myPosition == 0 ? "-" : String.valueOf(myPosition),
                    "my_value", Text.number(myValue))));
        }

        ItemStack item = Heads.fromTexture(s.getString("texture"));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(Text.replace(s.getString("name", ""), "arena", arena.displayName())));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack simpleItem(ConfigurationSection s, Arena arena) {
        ItemStack item = Heads.fromTexture(s.getString("texture"));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(Text.replace(s.getString("name", ""), "arena", arena.displayName())));
        List<String> lore = new ArrayList<String>();
        for (String line : s.getStringList("lore")) {
            lore.add(Text.color(Text.replace(line, "arena", arena.displayName())));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean place(Inventory inventory, int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return false;
        }
        inventory.setItem(slot, item);
        return true;
    }

    // ------------------------------------------------------------------ rankings

    private List<Entry> entries(Arena arena, Ranking ranking) {
        List<Entry> result = new ArrayList<Entry>();
        if (ranking == Ranking.PLAYER_KILLS) {
            List<PlayerStats> players = new ArrayList<PlayerStats>(scores.players(arena.name()));
            Collections.sort(players, (a, b) -> Integer.compare(b.kills(), a.kills()));
            for (PlayerStats stats : players) {
                if (stats.kills() > 0) {
                    result.add(new Entry(stats.uuid().toString(), stats.name(), stats.kills()));
                }
            }
            return result;
        }
        List<ClanStats> list;
        if (ranking == Ranking.CLAN_POINTS) {
            list = scores.top(arena.name());
        } else {
            list = new ArrayList<ClanStats>(scores.clans(arena.name()));
            final Ranking by = ranking;
            Collections.sort(list, new Comparator<ClanStats>() {
                @Override
                public int compare(ClanStats a, ClanStats b) {
                    return by == Ranking.CLAN_KILLS
                            ? Integer.compare(b.kills(), a.kills())
                            : Integer.compare(b.dominations(), a.dominations());
                }
            });
        }
        for (ClanStats stats : list) {
            long value = ranking == Ranking.CLAN_POINTS ? stats.points()
                    : ranking == Ranking.CLAN_KILLS ? stats.kills() : stats.dominations();
            if (value > 0) {
                result.add(new Entry(stats.clan(), clans.display(stats.clan()), value));
            }
        }
        return result;
    }

    private static Ranking parseRanking(String value) {
        try {
            return Ranking.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Ranking.CLAN_POINTS;
        }
    }

    private YamlConfiguration defaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = plugin.getResource(FILE)) {
            if (in != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                yaml.loadFromString(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao ler " + FILE + " interno.", e);
        }
        return yaml;
    }
}
