package br.com.paragonn.dominacao.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Configuração de uma arena (arenas.&lt;nome&gt; no config.yml). Imutável. */
public final class ArenaSettings {

    public final boolean enabled;
    public final String displayName;
    public final long dominationMillis;
    public final int killPoints;
    public final int dominationPoints;
    public final boolean antiFarmEnabled;
    public final long killCooldownMillis;
    public final boolean requireKillerInside;
    public final boolean ignoreAllies;
    public final boolean resetWhenClanLeaves;
    public final boolean pauseWhenContested;
    public final boolean ownerCanRecapture;
    public final boolean resetAllOnCapture;
    public final long resetNotifyMinMillis;
    public final boolean tagEnabled;
    public final String tag;
    public final boolean tagInChat;
    public final boolean tagAboveHead;
    public final List<String> gainCommands;
    public final List<String> loseCommands;
    public final List<String> captureCommands;
    public final int npcUpdateSeconds;
    public final double hologramHeight;
    public final String topNpcName;
    /** Nick do jogador cuja skin o NPC de top usa ("" = skin padrão). */
    public final String topNpcSkin;
    public final String championDefaultName;
    /** Holograma avulso de top (null = não posicionado). */
    public final String boardWorld;
    public final double boardX;
    public final double boardY;
    public final double boardZ;
    public final boolean captureLightning;
    public final int captureLightningStrikes;
    public final boolean combatWallEnabled;
    public final long combatDurationMillis;
    public final int wallRadius;
    public final Material wallMaterial;
    public final byte wallData;
    public final Set<PlayerTeleportEvent.TeleportCause> blockedTeleportCauses;

    private ArenaSettings(String name, ConfigurationSection s) {
        this.enabled = s.getBoolean("enabled", true);
        this.displayName = s.getString("display-name", name);
        this.dominationMillis = Math.max(1, s.getLong("domination-time", 900)) * 1000L;
        this.killPoints = s.getInt("points.kill", 5);
        this.dominationPoints = s.getInt("points.domination", 50);
        this.antiFarmEnabled = s.getBoolean("anti-farm.enabled", true);
        this.killCooldownMillis = Math.max(0, s.getLong("anti-farm.kill-cooldown", 300)) * 1000L;
        this.requireKillerInside = s.getBoolean("kills.require-killer-inside", true);
        this.ignoreAllies = s.getBoolean("kills.ignore-allies", true);
        this.resetWhenClanLeaves = s.getBoolean("timer.reset-when-clan-leaves", true);
        this.pauseWhenContested = s.getBoolean("timer.pause-when-contested", false);
        this.ownerCanRecapture = s.getBoolean("timer.owner-can-recapture", false);
        this.resetAllOnCapture = s.getBoolean("timer.reset-all-on-capture", false);
        this.resetNotifyMinMillis = Math.max(0, s.getLong("timer.reset-notify-min-seconds", 5)) * 1000L;
        this.tagEnabled = s.getBoolean("rewards.domination-tag.enabled", true);
        this.tag = s.getString("rewards.domination-tag.tag", "&6[Dominante]");
        this.tagInChat = s.getBoolean("rewards.domination-tag.show-in-chat", true);
        this.tagAboveHead = s.getBoolean("rewards.domination-tag.show-above-head", true);
        this.gainCommands = list(s, "rewards.domination-tag.gain-commands");
        this.loseCommands = list(s, "rewards.domination-tag.lose-commands");
        this.captureCommands = list(s, "rewards.capture-commands");
        this.npcUpdateSeconds = Math.max(5, s.getInt("npcs.update-interval", 30));
        this.hologramHeight = s.getDouble("npcs.hologram-height", 1.9);
        this.topNpcName = s.getString("npcs.top-name", "TopDominacao");
        this.topNpcSkin = s.getString("npcs.top-skin", "").trim();
        this.championDefaultName = s.getString("npcs.champion-default-name", "Campeao");
        this.boardWorld = s.getString("top-hologram.world", null);
        this.boardX = s.getDouble("top-hologram.x");
        this.boardY = s.getDouble("top-hologram.y");
        this.boardZ = s.getDouble("top-hologram.z");
        this.captureLightning = s.getBoolean("npcs.capture-lightning.enabled", true);
        this.captureLightningStrikes = Math.max(1, Math.min(10, s.getInt("npcs.capture-lightning.strikes", 1)));
        this.combatWallEnabled = s.getBoolean("combat-wall.enabled", true);
        this.combatDurationMillis = Math.max(1, s.getLong("combat-wall.combat-seconds", 15)) * 1000L;
        this.wallRadius = Math.max(1, Math.min(16, s.getInt("combat-wall.radius", 6)));
        Material material = Material.matchMaterial(s.getString("combat-wall.material", "STAINED_GLASS"));
        this.wallMaterial = material != null && material.isBlock() ? material : Material.STAINED_GLASS;
        this.wallData = (byte) s.getInt("combat-wall.data", 14);
        this.blockedTeleportCauses = EnumSet.noneOf(PlayerTeleportEvent.TeleportCause.class);
        List<String> causes = s.isList("combat-wall.blocked-teleports")
                ? s.getStringList("combat-wall.blocked-teleports")
                : Arrays.asList("ENDER_PEARL", "COMMAND", "PLUGIN");
        for (String cause : causes) {
            try {
                blockedTeleportCauses.add(PlayerTeleportEvent.TeleportCause.valueOf(cause.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // causa inexistente nesta versão
            }
        }
    }

    public static ArenaSettings from(String name, ConfigurationSection section) {
        return new ArenaSettings(name, section);
    }

    /** Escreve os valores padrão em uma arena recém-criada. */
    public static void writeDefaults(ConfigurationSection s, String displayName) {
        s.set("enabled", true);
        s.set("display-name", displayName);
        s.set("domination-time", 900);
        s.set("points.kill", 5);
        s.set("points.domination", 50);
        s.set("anti-farm.enabled", true);
        s.set("anti-farm.kill-cooldown", 300);
        s.set("kills.require-killer-inside", true);
        s.set("kills.ignore-allies", true);
        s.set("timer.reset-when-clan-leaves", true);
        s.set("timer.pause-when-contested", false);
        s.set("timer.owner-can-recapture", false);
        s.set("timer.reset-all-on-capture", false);
        s.set("timer.reset-notify-min-seconds", 5);
        s.set("rewards.domination-tag.enabled", true);
        s.set("rewards.domination-tag.tag", "&6[Dominante]");
        s.set("rewards.domination-tag.show-in-chat", true);
        s.set("rewards.domination-tag.show-above-head", true);
        s.set("rewards.domination-tag.gain-commands", new ArrayList<String>());
        s.set("rewards.domination-tag.lose-commands", new ArrayList<String>());
        s.set("rewards.capture-commands", new ArrayList<String>());
        s.set("npcs.update-interval", 30);
        s.set("npcs.hologram-height", 1.9);
        s.set("npcs.top-name", "TopDominacao");
        s.set("npcs.top-skin", "");
        s.set("npcs.champion-default-name", "Campeao");
        s.set("npcs.capture-lightning.enabled", true);
        s.set("npcs.capture-lightning.strikes", 1);
        s.set("combat-wall.enabled", true);
        s.set("combat-wall.combat-seconds", 15);
        s.set("combat-wall.radius", 6);
        s.set("combat-wall.material", "STAINED_GLASS");
        s.set("combat-wall.data", 14);
        s.set("combat-wall.blocked-teleports", Arrays.asList("ENDER_PEARL", "COMMAND", "PLUGIN"));
    }

    private static List<String> list(ConfigurationSection s, String path) {
        List<String> values = s.getStringList(path);
        return values == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
