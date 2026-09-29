package br.com.paragonn.dominacao.domination;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.arena.ArenaManager;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aplica a tag de dominação (rewards.domination-tag.tag) em TODOS os membros online do clan dominante:
 * - no chat (antes do formato do chat), se show-in-chat: true
 * - acima da cabeça e no TAB (prefixo de time no scoreboard principal), se show-above-head: true
 * Reavalia periodicamente, então quem entra/sai do clan ou entra no servidor ganha/perde a tag sozinho.
 */
public final class TagDisplayManager implements Listener {

    private static final String TEAM_PREFIX = "dom_";
    private static final int MAX_PREFIX = 16; // limite do prefixo de time na 1.8
    private static final long REFRESH_TICKS = 40L;

    private final Plugin plugin;
    private final ArenaManager arenas;
    private final ClanManager clans;
    /** Tag de chat já colorida por jogador (lida na thread assíncrona do chat). */
    private final Map<UUID, String> chatTags = new ConcurrentHashMap<UUID, String>();
    /** Time + prefixo já aplicados por jogador ("" = sem time): só mexe no scoreboard quando muda. */
    private final Map<UUID, String> applied = new HashMap<UUID, String>();
    private BukkitTask task;

    public TagDisplayManager(Plugin plugin, ArenaManager arenas, ClanManager clans) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.clans = clans;
    }

    public void start() {
        stop();
        removeTeams();
        applied.clear();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, REFRESH_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Desligamento: remove os times (não ficam salvos no scoreboard do mundo). */
    public void shutdown() {
        stop();
        removeTeams();
        chatTags.clear();
        applied.clear();
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        Arena arena = dominatedArena(player);
        String tag = arena == null ? null : Text.color(arena.settings().tag);

        if (arena != null && arena.settings().tagInChat && !tag.isEmpty()) {
            chatTags.put(player.getUniqueId(), tag);
        } else {
            chatTags.remove(player.getUniqueId());
        }

        String wanted = arena != null && arena.settings().tagAboveHead && !tag.isEmpty() ? teamName(arena) : null;
        String prefix = null;
        if (wanted != null) {
            prefix = tag + " ";
            prefix = prefix.length() > MAX_PREFIX ? prefix.substring(0, MAX_PREFIX) : prefix;
        }
        String state = wanted == null ? "" : wanted + '\0' + prefix + '\0' + player.getName();
        if (state.equals(applied.get(player.getUniqueId()))) {
            return; // nada mudou desde a última vez
        }
        applied.put(player.getUniqueId(), state);

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<Team>(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX) && !team.getName().equals(wanted)
                    && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
        if (wanted != null) {
            Team team = board.getTeam(wanted);
            if (team == null) {
                team = board.registerNewTeam(wanted);
            }
            if (!prefix.equals(team.getPrefix())) {
                team.setPrefix(prefix);
            }
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String tag = chatTags.get(event.getPlayer().getUniqueId());
        if (tag != null) {
            event.setFormat(tag.replace("%", "%%") + " " + event.getFormat());
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                refresh(event.getPlayer());
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatTags.remove(event.getPlayer().getUniqueId());
        applied.remove(event.getPlayer().getUniqueId());
        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX) && team.hasEntry(event.getPlayer().getName())) {
                team.removeEntry(event.getPlayer().getName());
            }
        }
    }

    /** Primeira arena (com tag habilitada) dominada pelo clan do jogador. */
    private Arena dominatedArena(Player player) {
        String clan = clans.clanKey(player);
        if (clan == null) {
            return null;
        }
        for (Arena arena : arenas.all()) {
            if (arena.settings().tagEnabled && clan.equals(arena.dominantClan())) {
                return arena;
            }
        }
        return null;
    }

    private static String teamName(Arena arena) {
        String name = TEAM_PREFIX + arena.name();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private static void removeTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<Team>(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }
}
