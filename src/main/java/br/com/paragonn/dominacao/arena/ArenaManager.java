package br.com.paragonn.dominacao.arena;

import br.com.paragonn.dominacao.config.ConfigManager;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registro das arenas carregadas do config.yml. */
public final class ArenaManager {

    private final ConfigManager config;
    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    /** Cópia imutável usada pelas tarefas periódicas (recriada só quando as arenas mudam). */
    private volatile Collection<Arena> snapshot = Collections.emptyList();

    public ArenaManager(ConfigManager config) {
        this.config = config;
    }

    /** (Re)carrega do config preservando o estado em tempo real das arenas que continuam existindo. */
    public void reload() {
        Set<String> names = config.arenaNames();
        Iterator<Map.Entry<String, Arena>> iterator = arenas.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!names.contains(iterator.next().getKey())) {
                iterator.remove();
            }
        }
        for (String name : names) {
            Arena existing = arenas.get(name);
            if (existing == null) {
                arenas.put(name, new Arena(name, config.arenaSettings(name), config.arenaRegion(name)));
            } else {
                existing.settings(config.arenaSettings(name));
                existing.region(config.arenaRegion(name));
            }
        }
        updateSnapshot();
    }

    private void updateSnapshot() {
        snapshot = Collections.unmodifiableList(new ArrayList<Arena>(arenas.values()));
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    /** Arena informada ou, se null, a arena padrão (settings.default-arena). */
    public Arena resolve(String nameOrNull) {
        if (nameOrNull != null) {
            return get(nameOrNull);
        }
        Arena preferred = get(config.settings().defaultArena);
        if (preferred != null) {
            return preferred;
        }
        return arenas.isEmpty() ? null : arenas.values().iterator().next();
    }

    public Collection<Arena> all() {
        return snapshot;
    }

    public Collection<String> names() {
        return new ArrayList<String>(arenas.keySet());
    }

    /** Primeira arena ativa que contém a localização. */
    public Arena arenaAt(Location location) {
        for (Arena arena : snapshot) {
            if (arena.isActive() && arena.contains(location)) {
                return arena;
            }
        }
        return null;
    }

    public Arena create(String name, Cuboid region) {
        String key = name.toLowerCase(Locale.ROOT);
        config.saveArenaRegion(key, region);
        reload();
        return arenas.get(key);
    }

    public void delete(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        config.deleteArena(key);
        arenas.remove(key);
        updateSnapshot();
    }
}
