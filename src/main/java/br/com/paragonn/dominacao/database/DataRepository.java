package br.com.paragonn.dominacao.database;

import br.com.paragonn.dominacao.score.ClanStats;
import br.com.paragonn.dominacao.score.PlayerStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Todo o SQL do plugin. Leituras síncronas (boot); escritas assíncronas. */
public final class DataRepository {

    /** Estado persistido de uma arena. */
    public static final class ArenaState {
        public final String dominantClan;
        public final long dominatedAt;

        ArenaState(String dominantClan, long dominatedAt) {
            this.dominantClan = dominantClan;
            this.dominatedAt = dominatedAt;
        }
    }

    private final DatabaseManager db;

    public DataRepository(DatabaseManager db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ leituras

    public List<ClanStats> loadClanStats() throws SQLException {
        final List<ClanStats> result = new ArrayList<ClanStats>();
        db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT arena, clan, points, dominations, kills, deaths FROM " + db.table("clans"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ClanStats(rs.getString(1), rs.getString(2), rs.getLong(3),
                            rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
        });
        return result;
    }

    public List<PlayerStats> loadPlayerStats() throws SQLException {
        final List<PlayerStats> result = new ArrayList<PlayerStats>();
        db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT arena, uuid, name, clan, kills, deaths, time_ms FROM " + db.table("players"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.add(new PlayerStats(rs.getString(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                                rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getLong(7)));
                    } catch (IllegalArgumentException invalidUuid) {
                        // linha corrompida: ignorada
                    }
                }
            }
        });
        return result;
    }

    public Map<String, ArenaState> loadArenaStates() throws SQLException {
        final Map<String, ArenaState> result = new HashMap<String, ArenaState>();
        db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT arena, dominant_clan, dominated_at FROM " + db.table("arenas"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), new ArenaState(rs.getString(2), rs.getLong(3)));
                }
            }
        });
        return result;
    }

    /** Chave "arena:tipo" -> id do NPC no Citizens. */
    public Map<String, Integer> loadNpcs() throws SQLException {
        final Map<String, Integer> result = new HashMap<String, Integer>();
        db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT arena, type, npc_id FROM " + db.table("npcs"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1) + ":" + rs.getString(2), rs.getInt(3));
                }
            }
        });
        return result;
    }

    // ------------------------------------------------------------------ escritas

    public void saveClanStats(final List<ClanStats> stats, boolean async) {
        if (stats.isEmpty()) {
            return;
        }
        run(async, c -> {
            try (PreparedStatement ps = c.prepareStatement(upsert("clans",
                    new String[]{"arena", "clan", "points", "dominations", "kills", "deaths"}, 2))) {
                for (ClanStats s : stats) {
                    ps.setString(1, s.arena());
                    ps.setString(2, s.clan());
                    ps.setLong(3, s.points());
                    ps.setInt(4, s.dominations());
                    ps.setInt(5, s.kills());
                    ps.setInt(6, s.deaths());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public void savePlayerStats(final List<PlayerStats> stats, boolean async) {
        if (stats.isEmpty()) {
            return;
        }
        run(async, c -> {
            try (PreparedStatement ps = c.prepareStatement(upsert("players",
                    new String[]{"arena", "uuid", "name", "clan", "kills", "deaths", "time_ms"}, 2))) {
                for (PlayerStats s : stats) {
                    ps.setString(1, s.arena());
                    ps.setString(2, s.uuid().toString());
                    ps.setString(3, s.name());
                    ps.setString(4, s.clan());
                    ps.setInt(5, s.kills());
                    ps.setInt(6, s.deaths());
                    ps.setLong(7, s.timeMillis());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public void saveArenaState(final String arena, final String dominantClan, final long dominatedAt) {
        db.async(c -> {
            try (PreparedStatement ps = c.prepareStatement(upsert("arenas",
                    new String[]{"arena", "dominant_clan", "dominated_at"}, 1))) {
                ps.setString(1, arena);
                ps.setString(2, dominantClan);
                ps.setLong(3, dominatedAt);
                ps.executeUpdate();
            }
        });
    }

    public void saveNpc(final String arena, final String type, final int npcId) {
        db.async(c -> {
            try (PreparedStatement ps = c.prepareStatement(upsert("npcs",
                    new String[]{"arena", "type", "npc_id"}, 2))) {
                ps.setString(1, arena);
                ps.setString(2, type);
                ps.setInt(3, npcId);
                ps.executeUpdate();
            }
        });
    }

    public void deleteClan(final String arena, final String clan) {
        db.async(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM " + db.table("clans") + " WHERE arena = ? AND clan = ?")) {
                ps.setString(1, arena);
                ps.setString(2, clan);
                ps.executeUpdate();
            }
        });
    }

    /** Apaga pontos, estatísticas e o clan dominante da arena (os NPCs são mantidos). */
    public void deleteArenaData(final String arena) {
        db.async(c -> {
            for (String table : new String[]{"clans", "players", "arenas"}) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM " + db.table(table) + " WHERE arena = ?")) {
                    ps.setString(1, arena);
                    ps.executeUpdate();
                }
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Executa em uma transação (um único commit para o lote inteiro). */
    private void run(boolean async, final DatabaseManager.SqlTask task) {
        DatabaseManager.SqlTask transactional = c -> {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                task.run(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        };
        if (async) {
            db.async(transactional);
            return;
        }
        try {
            db.sync(transactional);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** INSERT OR REPLACE (SQLite) / ON DUPLICATE KEY UPDATE (MySQL). As primeiras keyCount colunas são a chave. */
    private String upsert(String table, String[] columns, int keyCount) {
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            cols.append(i == 0 ? "" : ", ").append(columns[i]);
            marks.append(i == 0 ? "?" : ", ?");
        }
        String values = " (" + cols + ") VALUES (" + marks + ")";
        if (!db.isMySql()) {
            return "INSERT OR REPLACE INTO " + db.table(table) + values;
        }
        StringBuilder update = new StringBuilder();
        for (int i = keyCount; i < columns.length; i++) {
            update.append(i == keyCount ? "" : ", ").append(columns[i]).append(" = VALUES(").append(columns[i]).append(")");
        }
        return "INSERT INTO " + db.table(table) + values + " ON DUPLICATE KEY UPDATE " + update;
    }
}
