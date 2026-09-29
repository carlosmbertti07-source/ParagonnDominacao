package br.com.paragonn.dominacao.score;

import java.util.UUID;

/** Estatísticas individuais de um jogador em uma arena. */
public final class PlayerStats {

    private final String arena;
    private final UUID uuid;
    private volatile String name;
    private volatile String clan;
    private volatile int kills;
    private volatile int deaths;
    private volatile long timeMillis;

    public PlayerStats(String arena, UUID uuid, String name) {
        this.arena = arena;
        this.uuid = uuid;
        this.name = name;
    }

    public PlayerStats(String arena, UUID uuid, String name, String clan, int kills, int deaths, long timeMillis) {
        this(arena, uuid, name);
        this.clan = clan;
        this.kills = kills;
        this.deaths = deaths;
        this.timeMillis = timeMillis;
    }

    public PlayerStats copy() {
        return new PlayerStats(arena, uuid, name, clan, kills, deaths, timeMillis);
    }

    public String arena() {
        return arena;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String clan() {
        return clan;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public long timeMillis() {
        return timeMillis;
    }

    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    void identity(String name, String clan) {
        if (name != null) {
            this.name = name;
        }
        this.clan = clan;
    }

    void addKill() {
        kills++;
    }

    void addDeath() {
        deaths++;
    }

    void addTime(long millis) {
        timeMillis += millis;
    }
}
