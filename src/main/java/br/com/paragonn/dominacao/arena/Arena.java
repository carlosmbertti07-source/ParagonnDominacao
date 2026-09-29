package br.com.paragonn.dominacao.arena;

import br.com.paragonn.dominacao.config.ArenaSettings;
import org.bukkit.Location;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uma arena de dominação: configuração + estado em tempo real.
 * O estado é alterado apenas na thread principal; leituras de placeholders usam
 * estruturas thread-safe.
 */
public final class Arena {

    private final String name;
    private volatile ArenaSettings settings;
    private volatile Cuboid region;

    /** Progresso de dominação por clan (ms contínuos). */
    private final Map<String, Long> progress = new ConcurrentHashMap<String, Long>();
    /** Último resultado da varredura: clan -> jogadores do clan dentro da arena. */
    private volatile Map<String, Set<UUID>> presence = Collections.emptyMap();
    /** Todos os jogadores dentro (inclusive sem clan) — destinatários da ActionBar. */
    private volatile Set<UUID> playersInside = Collections.emptySet();

    private volatile String dominantClan;
    private volatile long dominatedAt;

    public Arena(String name, ArenaSettings settings, Cuboid region) {
        this.name = name;
        this.settings = settings;
        this.region = region;
    }

    public String name() {
        return name;
    }

    public ArenaSettings settings() {
        return settings;
    }

    public void settings(ArenaSettings settings) {
        this.settings = settings;
    }

    public Cuboid region() {
        return region;
    }

    public void region(Cuboid region) {
        this.region = region;
    }

    public String displayName() {
        return settings.displayName;
    }

    public boolean isActive() {
        return settings.enabled && region != null;
    }

    public boolean contains(Location location) {
        Cuboid current = region;
        return current != null && current.contains(location);
    }

    public Map<String, Long> progress() {
        return progress;
    }

    public long progressOf(String clan) {
        Long value = clan == null ? null : progress.get(clan);
        return value == null ? 0L : value;
    }

    public Map<String, Set<UUID>> presence() {
        return presence;
    }

    public Set<UUID> playersInside() {
        return playersInside;
    }

    public void updatePresence(LinkedHashMap<String, Set<UUID>> presence, Set<UUID> playersInside) {
        this.presence = Collections.unmodifiableMap(presence);
        this.playersInside = Collections.unmodifiableSet(playersInside);
    }

    public String dominantClan() {
        return dominantClan;
    }

    public long dominatedAt() {
        return dominatedAt;
    }

    public void dominate(String clan, long at) {
        this.dominantClan = clan;
        this.dominatedAt = at;
    }
}
