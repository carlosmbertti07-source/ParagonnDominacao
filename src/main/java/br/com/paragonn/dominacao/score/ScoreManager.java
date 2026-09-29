package br.com.paragonn.dominacao.score;

import br.com.paragonn.dominacao.database.DataRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pontos e estatísticas em memória (cache) com gravação "write-behind":
 * alterações marcam o registro como sujo e o flush periódico grava só o que mudou.
 * O ranking é cacheado por arena e invalidado quando os pontos mudam.
 */
public final class ScoreManager {

    private static final Comparator<ClanStats> BY_POINTS = (a, b) -> {
        int byPoints = Long.compare(b.points(), a.points());
        if (byPoints != 0) {
            return byPoints;
        }
        int byDominations = Integer.compare(b.dominations(), a.dominations());
        return byDominations != 0 ? byDominations : a.clan().compareTo(b.clan());
    };

    private final DataRepository repository;
    private final Map<String, Map<String, ClanStats>> clans = new ConcurrentHashMap<String, Map<String, ClanStats>>();
    private final Map<String, Map<UUID, PlayerStats>> players = new ConcurrentHashMap<String, Map<UUID, PlayerStats>>();
    private final Map<String, List<ClanStats>> topCache = new ConcurrentHashMap<String, List<ClanStats>>();
    private final Set<ClanStats> dirtyClans = new HashSet<ClanStats>();
    private final Set<PlayerStats> dirtyPlayers = new HashSet<PlayerStats>();

    public ScoreManager(DataRepository repository) {
        this.repository = repository;
    }

    public void loadAll() throws SQLException {
        clans.clear();
        players.clear();
        topCache.clear();
        for (ClanStats stats : repository.loadClanStats()) {
            clanMap(stats.arena()).put(stats.clan(), stats);
        }
        for (PlayerStats stats : repository.loadPlayerStats()) {
            playerMap(stats.arena()).put(stats.uuid(), stats);
        }
    }

    // ------------------------------------------------------------------ consultas

    public ClanStats clan(String arena, String clan) {
        Map<String, ClanStats> map = clans.get(arena);
        ClanStats stats = map == null ? null : map.get(clan);
        return stats == null ? new ClanStats(arena, clan) : stats;
    }

    public PlayerStats player(String arena, UUID uuid) {
        Map<UUID, PlayerStats> map = players.get(arena);
        return map == null ? null : map.get(uuid);
    }

    /** Ranking ordenado por pontos (cacheado até a próxima alteração de pontos). */
    public List<ClanStats> top(String arena) {
        List<ClanStats> cached = topCache.get(arena);
        if (cached != null) {
            return cached;
        }
        Map<String, ClanStats> map = clans.get(arena);
        List<ClanStats> sorted = new ArrayList<ClanStats>();
        if (map != null) {
            for (ClanStats stats : map.values()) {
                if (stats.points() > 0 || stats.dominations() > 0) {
                    sorted.add(stats);
                }
            }
        }
        Collections.sort(sorted, BY_POINTS);
        List<ClanStats> result = Collections.unmodifiableList(sorted);
        topCache.put(arena, result);
        return result;
    }

    /** Todas as estatísticas de clans da arena (para rankings alternativos, ex.: menu). */
    public Collection<ClanStats> clans(String arena) {
        Map<String, ClanStats> map = clans.get(arena);
        return map == null ? Collections.<ClanStats>emptyList() : new ArrayList<ClanStats>(map.values());
    }

    /** Todas as estatísticas de jogadores da arena. */
    public Collection<PlayerStats> players(String arena) {
        Map<UUID, PlayerStats> map = players.get(arena);
        return map == null ? Collections.<PlayerStats>emptyList() : new ArrayList<PlayerStats>(map.values());
    }

    /** Posição (1..n) do clan no ranking, ou 0. */
    public int position(String arena, String clan) {
        List<ClanStats> top = top(arena);
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).clan().equals(clan)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Membro com mais abates na arena entre os UUIDs informados (null se ninguém tem abates). */
    public PlayerStats bestPlayer(String arena, Collection<UUID> members) {
        Map<UUID, PlayerStats> map = players.get(arena);
        if (map == null) {
            return null;
        }
        PlayerStats best = null;
        for (UUID uuid : members) {
            PlayerStats stats = map.get(uuid);
            if (stats != null && stats.kills() > 0 && (best == null || stats.kills() > best.kills())) {
                best = stats;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ alterações (thread principal)

    public void addKill(String arena, UUID uuid, String name, String clan, int points) {
        PlayerStats player = playerFor(arena, uuid, name);
        player.identity(name, clan);
        player.addKill();
        markDirty(player);

        ClanStats stats = clanFor(arena, clan);
        stats.addKill();
        stats.addPoints(points);
        markDirty(stats);
    }

    public void addDeath(String arena, UUID uuid, String name, String clan) {
        PlayerStats player = playerFor(arena, uuid, name);
        player.identity(name, clan);
        player.addDeath();
        markDirty(player);

        ClanStats stats = clanFor(arena, clan);
        stats.addDeath();
        markDirty(stats);
    }

    public void addTime(String arena, UUID uuid, String name, String clan, long millis) {
        PlayerStats player = playerFor(arena, uuid, name);
        player.identity(name, clan);
        player.addTime(millis);
        markDirty(player);
    }

    public void addDomination(String arena, String clan, int points) {
        ClanStats stats = clanFor(arena, clan);
        stats.addDomination();
        stats.addPoints(points);
        markDirty(stats);
    }

    public void addPoints(String arena, String clan, long amount) {
        ClanStats stats = clanFor(arena, clan);
        stats.addPoints(amount);
        markDirty(stats);
    }

    public void setPoints(String arena, String clan, long amount) {
        ClanStats stats = clanFor(arena, clan);
        stats.setPoints(amount);
        markDirty(stats);
    }

    public void resetClan(String arena, String clan) {
        Map<String, ClanStats> map = clans.get(arena);
        if (map != null) {
            ClanStats removed = map.remove(clan);
            synchronized (this) {
                dirtyClans.remove(removed);
            }
        }
        topCache.remove(arena);
        repository.deleteClan(arena, clan);
    }

    public void resetArena(String arena) {
        Map<String, ClanStats> clanMap = clans.remove(arena);
        Map<UUID, PlayerStats> playerMap = players.remove(arena);
        synchronized (this) {
            if (clanMap != null) {
                dirtyClans.removeAll(clanMap.values());
            }
            if (playerMap != null) {
                dirtyPlayers.removeAll(playerMap.values());
            }
        }
        topCache.remove(arena);
        repository.deleteArenaData(arena);
    }

    // ------------------------------------------------------------------ persistência

    /** Grava tudo que mudou desde o último flush. async=false no desligamento. */
    public void flush(boolean async) {
        List<ClanStats> clanSnapshot = new ArrayList<ClanStats>();
        List<PlayerStats> playerSnapshot = new ArrayList<PlayerStats>();
        synchronized (this) {
            for (ClanStats stats : dirtyClans) {
                clanSnapshot.add(stats.copy());
            }
            for (PlayerStats stats : dirtyPlayers) {
                playerSnapshot.add(stats.copy());
            }
            dirtyClans.clear();
            dirtyPlayers.clear();
        }
        repository.saveClanStats(clanSnapshot, async);
        repository.savePlayerStats(playerSnapshot, async);
    }

    // ------------------------------------------------------------------ internos

    private ClanStats clanFor(String arena, String clan) {
        Map<String, ClanStats> map = clanMap(arena);
        ClanStats stats = map.get(clan);
        if (stats == null) {
            stats = new ClanStats(arena, clan);
            map.put(clan, stats);
        }
        return stats;
    }

    private PlayerStats playerFor(String arena, UUID uuid, String name) {
        Map<UUID, PlayerStats> map = playerMap(arena);
        PlayerStats stats = map.get(uuid);
        if (stats == null) {
            stats = new PlayerStats(arena, uuid, name);
            map.put(uuid, stats);
        }
        return stats;
    }

    private Map<String, ClanStats> clanMap(String arena) {
        Map<String, ClanStats> map = clans.get(arena);
        if (map == null) {
            map = new ConcurrentHashMap<String, ClanStats>();
            clans.put(arena, map);
        }
        return map;
    }

    private Map<UUID, PlayerStats> playerMap(String arena) {
        Map<UUID, PlayerStats> map = players.get(arena);
        if (map == null) {
            map = new ConcurrentHashMap<UUID, PlayerStats>();
            players.put(arena, map);
        }
        return map;
    }

    private synchronized void markDirty(ClanStats stats) {
        dirtyClans.add(stats);
        topCache.remove(stats.arena());
    }

    private synchronized void markDirty(PlayerStats stats) {
        dirtyPlayers.add(stats);
    }
}
