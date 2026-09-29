package br.com.paragonn.dominacao.clan;

import br.com.paragonn.dominacao.config.ConfigManager;
import br.com.paragonn.dominacao.hook.SimpleClansHook;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fachada de clans usada pelo resto do plugin. Clans são identificados pela
 * tag sem cores em minúsculo ("chave"), que é única no SimpleClans.
 */
public final class ClanManager {

    /** Membro de clan (online ou offline). */
    public static final class Member {
        public final UUID uuid;
        public final String name;

        Member(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private final SimpleClansHook hook;
    private final ConfigManager config;

    public ClanManager(SimpleClansHook hook, ConfigManager config) {
        this.hook = hook;
        this.config = config;
    }

    public static String normalize(String tag) {
        return tag == null ? null : ChatColor.stripColor(tag).toLowerCase(Locale.ROOT);
    }

    /** Chave do clan do jogador, ou null se ele não tiver clan. */
    public String clanKey(Player player) {
        Clan clan = hook.clanOf(player.getUniqueId());
        return clan == null ? null : normalize(clan.getTag());
    }

    public boolean exists(String key) {
        return key != null && hook.clanByTag(key) != null;
    }

    /** Nome de exibição conforme settings.clan-display (TAG, COLOR_TAG ou NAME). */
    public String display(String key) {
        if (key == null) {
            return "-";
        }
        Clan clan = hook.clanByTag(key);
        if (clan == null) {
            return key.toUpperCase(Locale.ROOT);
        }
        switch (config.settings().clanDisplay) {
            case NAME:
                return clan.getName();
            case TAG:
                return clan.getTag();
            default:
                return clan.getColorTag();
        }
    }

    public boolean areAllies(String first, String second) {
        Clan clan = hook.clanByTag(first);
        return clan != null && clan.isAlly(second);
    }

    public List<Member> members(String key) {
        Clan clan = key == null ? null : hook.clanByTag(key);
        if (clan == null) {
            return Collections.emptyList();
        }
        List<Member> members = new ArrayList<Member>();
        for (ClanPlayer clanPlayer : clan.getMembers()) {
            members.add(new Member(clanPlayer.getUniqueId(), clanPlayer.getName()));
        }
        return members;
    }

    public List<UUID> memberIds(String key) {
        List<UUID> ids = new ArrayList<UUID>();
        for (Member member : members(key)) {
            ids.add(member.uuid);
        }
        return ids;
    }

    public List<Player> onlineMembers(String key) {
        List<Player> online = new ArrayList<Player>();
        for (Member member : members(key)) {
            Player player = Bukkit.getPlayer(member.uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }
}
