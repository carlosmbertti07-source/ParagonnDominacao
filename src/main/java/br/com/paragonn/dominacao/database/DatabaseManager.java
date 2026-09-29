package br.com.paragonn.dominacao.database;

import br.com.paragonn.dominacao.config.DatabaseSettings;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Conexão JDBC (SQLite padrão, MySQL opcional). Os drivers já vêm no Spigot 1.8.8.
 * Escritas rodam em uma única thread dedicada (ordem preservada, nunca na thread principal).
 */
public final class DatabaseManager {

    public interface SqlTask {
        void run(Connection connection) throws SQLException;
    }

    private final DatabaseSettings settings;
    private final File dataFolder;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ParagonnDominacao-DB");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public DatabaseManager(DatabaseSettings settings, File dataFolder, Logger logger) {
        this.settings = settings;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void connect() throws SQLException {
        loadDriver();
        sync(this::createTables);
        logger.info("Banco de dados conectado (" + (settings.mysql ? "MySQL" : "SQLite") + ").");
    }

    public boolean isMySql() {
        return settings.mysql;
    }

    public String table(String name) {
        return settings.tablePrefix + name;
    }

    /** Executa na thread do banco. */
    public void async(final SqlTask task) {
        if (executor.isShutdown()) {
            runLogged(task);
            return;
        }
        executor.execute(() -> runLogged(task));
    }

    /** Executa na thread atual (uso: carga inicial e desligamento). */
    public synchronized void sync(SqlTask task) throws SQLException {
        task.run(connection());
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Tempo esgotado aguardando gravacoes pendentes no banco.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Erro ao fechar o banco", e);
            }
        }
    }

    private void runLogged(SqlTask task) {
        try {
            synchronized (this) {
                task.run(connection());
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro no banco de dados", e);
        }
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || (settings.mysql && !connection.isValid(2))) {
            connection = open();
        }
        return connection;
    }

    private Connection open() throws SQLException {
        if (settings.mysql) {
            String url = "jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.database
                    + "?useSSL=" + settings.useSsl + "&autoReconnect=true&characterEncoding=utf8&useUnicode=true";
            return DriverManager.getConnection(url, settings.user, settings.password);
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new SQLException("Nao foi possivel criar " + dataFolder);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, settings.sqliteFile).getAbsolutePath());
    }

    private void loadDriver() throws SQLException {
        String[] candidates = settings.mysql
                ? new String[]{"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"}
                : new String[]{"org.sqlite.JDBC"};
        for (String driver : candidates) {
            try {
                Class.forName(driver);
                return;
            } catch (ClassNotFoundException ignored) {
                // tenta o próximo
            }
        }
        throw new SQLException("Driver JDBC nao encontrado para " + (settings.mysql ? "MySQL" : "SQLite"));
    }

    private void createTables(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("clans") + " ("
                    + "arena VARCHAR(32) NOT NULL, clan VARCHAR(32) NOT NULL, "
                    + "points BIGINT NOT NULL DEFAULT 0, dominations INT NOT NULL DEFAULT 0, "
                    + "kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (arena, clan))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("players") + " ("
                    + "arena VARCHAR(32) NOT NULL, uuid VARCHAR(36) NOT NULL, name VARCHAR(16) NOT NULL, "
                    + "clan VARCHAR(32), kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, "
                    + "time_ms BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (arena, uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("arenas") + " ("
                    + "arena VARCHAR(32) NOT NULL PRIMARY KEY, dominant_clan VARCHAR(32), dominated_at BIGINT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("npcs") + " ("
                    + "arena VARCHAR(32) NOT NULL, type VARCHAR(16) NOT NULL, npc_id INT NOT NULL, "
                    + "PRIMARY KEY (arena, type))");
        }
    }
}
