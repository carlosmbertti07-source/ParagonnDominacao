package br.com.paragonn.dominacao.score;

/** Estatísticas de um clan em uma arena. Mutável; alterada apenas na thread principal. */
public final class ClanStats {

    private final String arena;
    private final String clan;
    private volatile long points;
    private volatile int dominations;
    private volatile int kills;
    private volatile int deaths;

    public ClanStats(String arena, String clan) {
        this.arena = arena;
        this.clan = clan;
    }

    public ClanStats(String arena, String clan, long points, int dominations, int kills, int deaths) {
        this(arena, clan);
        this.points = points;
        this.dominations = dominations;
        this.kills = kills;
        this.deaths = deaths;
    }

    public ClanStats copy() {
        return new ClanStats(arena, clan, points, dominations, kills, deaths);
    }

    public String arena() {
        return arena;
    }

    public String clan() {
        return clan;
    }

    public long points() {
        return points;
    }

    public int dominations() {
        return dominations;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    void addPoints(long amount) {
        points = Math.max(0, points + amount);
    }

    void setPoints(long amount) {
        points = Math.max(0, amount);
    }

    void addDomination() {
        dominations++;
    }

    void addKill() {
        kills++;
    }

    void addDeath() {
        deaths++;
    }
}
