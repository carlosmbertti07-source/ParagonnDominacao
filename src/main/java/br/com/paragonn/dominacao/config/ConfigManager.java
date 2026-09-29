package br.com.paragonn.dominacao.config;

import br.com.paragonn.dominacao.arena.Cuboid;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lê e grava o config.yml. Toda a documentação do arquivo fica no cabeçalho
 * (comentários do topo), que o Bukkit preserva ao salvar via comando.
 */
public final class ConfigManager {

    private static final String ARENAS = "arenas";

    private final JavaPlugin plugin;
    private GlobalSettings settings;
    private DatabaseSettings database;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        config.options().copyHeader(true);
        this.settings = GlobalSettings.from(config.getConfigurationSection("settings"));
        this.database = DatabaseSettings.from(config.getConfigurationSection("database"));
    }

    public GlobalSettings settings() {
        return settings;
    }

    public DatabaseSettings database() {
        return database;
    }

    public Set<String> arenaNames() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ARENAS);
        if (section == null) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<String>();
        for (String key : section.getKeys(false)) {
            names.add(key.toLowerCase(Locale.ROOT));
        }
        return names;
    }

    public ArenaSettings arenaSettings(String name) {
        return ArenaSettings.from(name, arenaSection(name, false));
    }

    /** Retorna null se a arena ainda não tem região definida. */
    public Cuboid arenaRegion(String name) {
        ConfigurationSection section = arenaSection(name, false).getConfigurationSection("region");
        return section == null ? null : Cuboid.read(section);
    }

    public boolean hasArena(String name) {
        return plugin.getConfig().isConfigurationSection(ARENAS + "." + name);
    }

    /** Define a região; cria a arena com valores padrão se ela ainda não existir no config. */
    public void saveArenaRegion(String name, Cuboid region) {
        if (!hasArena(name)) {
            ArenaSettings.writeDefaults(arenaSection(name, true), name);
        }
        ConfigurationSection section = arenaSection(name, true);
        section.set("region", null);
        region.write(section.createSection("region"));
        plugin.saveConfig();
    }

    public void setArenaValue(String name, String path, Object value) {
        arenaSection(name, true).set(path, value);
        plugin.saveConfig();
    }

    public void deleteArena(String name) {
        plugin.getConfig().set(ARENAS + "." + name, null);
        plugin.saveConfig();
    }

    private ConfigurationSection arenaSection(String name, boolean create) {
        FileConfiguration config = plugin.getConfig();
        String path = ARENAS + "." + name;
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            section = create ? config.createSection(path) : new MemoryConfiguration();
        }
        return section;
    }
}
