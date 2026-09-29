package br.com.paragonn.dominacao.hook;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.score.ClanStats;
import br.com.paragonn.dominacao.score.PlayerStats;
import br.com.paragonn.dominacao.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Placeholders %dominacao_...%. Sem prefixo de arena usa a arena padrão;
 * com prefixo: %dominacao_minapvp_top_1_clan%.
 *
 * top_N_clan, top_N_pontos, top_N_dominacoes, dominante, dominante_tag, tag,
 * clan, pontos, posicao, dominacoes, kills, mortes, kdr, tempo, progresso
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final DominacaoPlugin plugin;

    private PlaceholderHook(DominacaoPlugin plugin) {
        this.plugin = plugin;
    }

    /** Chamado apenas se o PlaceholderAPI estiver instalado (evita NoClassDefFoundError). */
    public static void registerHook(DominacaoPlugin plugin) {
        new PlaceholderHook(plugin).register();
    }

    @Override
    public String getIdentifier() {
        return "dominacao";
    }

    @Override
    public String getAuthor() {
        return "Paragonn";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String lower = params.toLowerCase(Locale.ROOT);
        Arena arena = null;
        int separator = lower.indexOf('_');
        if (separator > 0) {
            arena = plugin.arenas().get(lower.substring(0, separator));
            if (arena != null) {
                lower = lower.substring(separator + 1);
            }
        }
        if (arena == null) {
            arena = plugin.arenas().resolve(null);
        }
        if (arena == null) {
            return "";
        }

        if (lower.startsWith("top_")) {
            return top(arena, lower);
        }
        switch (lower) {
            case "dominante":
                return arena.dominantClan() == null
                        ? plugin.messages().get("placeholder.none")
                        : plugin.clans().display(arena.dominantClan());
            case "dominante_tag":
                return arena.dominantClan() == null ? "" : arena.dominantClan().toUpperCase(Locale.ROOT);
            case "arena":
                return arena.displayName();
            default:
                break;
        }

        Player player = offline == null ? null : offline.getPlayer();
        if (player == null) {
            return "";
        }
        String clan = plugin.clans().clanKey(player);
        switch (lower) {
            case "tag":
                return plugin.rewards().tagFor(player);
            case "clan":
                return clan == null ? plugin.messages().get("placeholder.no-clan") : plugin.clans().display(clan);
            case "pontos":
                return clan == null ? "0" : Text.number(plugin.scores().clan(arena.name(), clan).points());
            case "posicao":
                return clan == null ? "-" : String.valueOf(plugin.scores().position(arena.name(), clan));
            case "dominacoes":
                return clan == null ? "0" : String.valueOf(plugin.scores().clan(arena.name(), clan).dominations());
            case "progresso":
                return Text.time(arena.progressOf(clan));
            default:
                break;
        }

        PlayerStats stats = plugin.scores().player(arena.name(), player.getUniqueId());
        switch (lower) {
            case "kills":
                return stats == null ? "0" : String.valueOf(stats.kills());
            case "mortes":
                return stats == null ? "0" : String.valueOf(stats.deaths());
            case "kdr":
                return stats == null ? "0,00" : Text.decimal(stats.kdr());
            case "tempo":
                return stats == null ? "0m 0s" : Text.duration(stats.timeMillis());
            default:
                return null;
        }
    }

    /** top_1_clan | top_1_pontos | top_1_dominacoes */
    private String top(Arena arena, String params) {
        String[] parts = params.split("_");
        if (parts.length != 3) {
            return null;
        }
        int position;
        try {
            position = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        List<ClanStats> top = plugin.scores().top(arena.name());
        if (position < 1 || position > top.size()) {
            return "clan".equals(parts[2]) ? plugin.messages().get("placeholder.empty-position") : "0";
        }
        ClanStats stats = top.get(position - 1);
        switch (parts[2]) {
            case "clan":
                return plugin.clans().display(stats.clan());
            case "pontos":
                return Text.number(stats.points());
            case "dominacoes":
                return String.valueOf(stats.dominations());
            default:
                return null;
        }
    }
}
