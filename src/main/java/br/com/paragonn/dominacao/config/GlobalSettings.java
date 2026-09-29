package br.com.paragonn.dominacao.config;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Seção "settings" do config.yml. */
public final class GlobalSettings {

    public enum ClanDisplay { TAG, COLOR_TAG, NAME }

    /** Quem recebe a ActionBar: todos online, jogadores do mundo da arena ou só quem está dentro. */
    public enum Recipients { ALL, WORLD, INSIDE }

    public final String defaultArena;
    public final Recipients disputeRecipients;
    public final Recipients holdingRecipients;
    public final int combatWallRefreshTicks;
    public final Set<GameMode> ignoredGameModes;
    public final ClanDisplay clanDisplay;
    public final int topSize;
    public final boolean broadcastDomination;
    public final double hologramLineSpacing;

    private GlobalSettings(ConfigurationSection s) {
        this.defaultArena = s.getString("default-arena", "minapvp").toLowerCase(Locale.ROOT);
        this.disputeRecipients = parseRecipients(s.getString("actionbar-dispute-recipients", "ALL"), Recipients.ALL);
        this.holdingRecipients = parseRecipients(s.getString("actionbar-holding-recipients", "INSIDE"), Recipients.INSIDE);
        this.combatWallRefreshTicks = Math.max(1, s.getInt("combat-wall-refresh-ticks", 5));
        this.ignoredGameModes = EnumSet.noneOf(GameMode.class);
        for (String mode : s.getStringList("ignored-gamemodes")) {
            try {
                ignoredGameModes.add(GameMode.valueOf(mode.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // modo inexistente nesta versão
            }
        }
        this.clanDisplay = parseDisplay(s.getString("clan-display", "COLOR_TAG"));
        this.topSize = Math.max(1, s.getInt("top-size", 10));
        this.broadcastDomination = s.getBoolean("broadcast-domination", true);
        this.hologramLineSpacing = s.getDouble("hologram-line-spacing", 0.27);
    }

    public static GlobalSettings from(ConfigurationSection section) {
        return new GlobalSettings(section == null ? new MemoryConfiguration() : section);
    }

    private static Recipients parseRecipients(String value, Recipients fallback) {
        try {
            return Recipients.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static ClanDisplay parseDisplay(String value) {
        try {
            return ClanDisplay.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ClanDisplay.COLOR_TAG;
        }
    }
}
