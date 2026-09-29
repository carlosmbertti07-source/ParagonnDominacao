package br.com.paragonn.dominacao.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Locale;

/** Seção "database" do config.yml. */
public final class DatabaseSettings {

    public final boolean mysql;
    public final String sqliteFile;
    public final String tablePrefix;
    public final int saveIntervalSeconds;
    public final String host;
    public final int port;
    public final String database;
    public final String user;
    public final String password;
    public final boolean useSsl;

    private DatabaseSettings(ConfigurationSection s) {
        this.mysql = "MYSQL".equals(s.getString("type", "SQLITE").toUpperCase(Locale.ROOT));
        this.sqliteFile = s.getString("sqlite-file", "database.db");
        this.tablePrefix = s.getString("table-prefix", "dom_").replaceAll("[^A-Za-z0-9_]", "");
        this.saveIntervalSeconds = Math.max(5, s.getInt("save-interval-seconds", 60));
        this.host = s.getString("mysql.host", "localhost");
        this.port = s.getInt("mysql.port", 3306);
        this.database = s.getString("mysql.database", "minecraft");
        this.user = s.getString("mysql.user", "root");
        this.password = s.getString("mysql.password", "");
        this.useSsl = s.getBoolean("mysql.use-ssl", false);
    }

    public static DatabaseSettings from(ConfigurationSection section) {
        return new DatabaseSettings(section == null ? new MemoryConfiguration() : section);
    }
}
