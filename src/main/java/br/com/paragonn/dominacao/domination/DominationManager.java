package br.com.paragonn.dominacao.domination;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.arena.ArenaManager;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.config.ArenaSettings;
import br.com.paragonn.dominacao.config.ConfigManager;
import br.com.paragonn.dominacao.config.GlobalSettings;
import br.com.paragonn.dominacao.config.MessageManager;
import br.com.paragonn.dominacao.database.DataRepository;
import br.com.paragonn.dominacao.npc.NpcManager;
import br.com.paragonn.dominacao.score.ScoreManager;
import br.com.paragonn.dominacao.util.ActionBar;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Coração do plugin: uma única tarefa por segundo varre as arenas (somente jogadores do mundo da arena),
 * soma exatamente 1 segundo ao cronômetro de cada clan, reseta quando o clan sai por completo,
 * captura a arena e envia a ActionBar no mesmo tick — o valor exibido sobe de 1 em 1.
 */
public final class DominationManager {

    /** Um passo do cronômetro: 20 ticks = 1 segundo. */
    private static final long STEP_TICKS = 20L;
    private static final long STEP_MILLIS = 1000L;

    private final Plugin plugin;
    private final ConfigManager config;
    private final ArenaManager arenas;
    private final ClanManager clans;
    private final ScoreManager scores;
    private final TagRewardManager rewards;
    private final MessageManager messages;
    private final DataRepository repository;
    private final ActionBar actionBar;
    private NpcManager npcs;
    private BukkitTask task;

    public DominationManager(Plugin plugin, ConfigManager config, ArenaManager arenas, ClanManager clans,
                             ScoreManager scores, TagRewardManager rewards, MessageManager messages,
                             DataRepository repository, ActionBar actionBar) {
        this.plugin = plugin;
        this.config = config;
        this.arenas = arenas;
        this.clans = clans;
        this.scores = scores;
        this.rewards = rewards;
        this.messages = messages;
        this.repository = repository;
        this.actionBar = actionBar;
    }

    public void setNpcManager(NpcManager npcs) {
        this.npcs = npcs;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, STEP_TICKS, STEP_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------ varredura

    /** Varre, soma 1 segundo e já mostra o resultado: cronômetro e ActionBar sempre em sincronia. */
    private void tick() {
        for (Arena arena : arenas.all()) {
            if (arena.isActive()) {
                scan(arena);
            } else if (!arena.presence().isEmpty()) {
                arena.updatePresence(new LinkedHashMap<String, Set<UUID>>(), new HashSet<UUID>());
                arena.progress().clear();
            }
        }
        sendActionBars();
    }

    private void scan(Arena arena) {
        ArenaSettings settings = arena.settings();
        World world = Bukkit.getWorld(arena.region().world());
        LinkedHashMap<String, Set<UUID>> presence = new LinkedHashMap<String, Set<UUID>>();
        Set<UUID> inside = new HashSet<UUID>();
        Set<UUID> previouslyInside = arena.playersInside();

        if (world != null) {
            Set<org.bukkit.GameMode> ignored = config.settings().ignoredGameModes;
            for (Player player : world.getPlayers()) {
                if (player.isDead() || ignored.contains(player.getGameMode()) || !arena.contains(player.getLocation())) {
                    continue;
                }
                UUID uuid = player.getUniqueId();
                inside.add(uuid);
                String clan = clans.clanKey(player);
                if (clan == null) {
                    continue; // jogadores sem clan não participam
                }
                Set<UUID> members = presence.get(clan);
                if (members == null) {
                    members = new HashSet<UUID>();
                    presence.put(clan, members);
                }
                members.add(uuid);
                if (previouslyInside.contains(uuid)) {
                    scores.addTime(arena.name(), uuid, player.getName(), clan, STEP_MILLIS);
                }
            }
        }

        Set<String> previousClans = arena.presence().keySet();
        handleDepartures(arena, presence.keySet(), settings);
        accumulate(arena, presence.keySet(), previousClans, settings);
        arena.updatePresence(presence, inside);
        checkCapture(arena, settings);
    }

    /** Clans que não têm mais nenhum membro dentro perdem o progresso. */
    private void handleDepartures(Arena arena, Set<String> present, ArenaSettings settings) {
        if (!settings.resetWhenClanLeaves) {
            return;
        }
        for (String clan : new ArrayList<String>(arena.progress().keySet())) {
            if (present.contains(clan)) {
                continue;
            }
            Long lost = arena.progress().remove(clan);
            if (lost != null && lost >= settings.resetNotifyMinMillis) {
                for (Player member : clans.onlineMembers(clan)) {
                    messages.send(member, "reset", "arena", arena.displayName(), "time", Text.time(lost));
                }
            }
        }
    }

    /**
     * +1 segundo para cada clan que já estava na área no segundo anterior. Quem acabou de entrar
     * começa em 00:00 e sobe para 00:01 no próximo segundo (sem pular números).
     */
    private void accumulate(Arena arena, Set<String> present, Set<String> previous, ArenaSettings settings) {
        if (present.isEmpty() || (settings.pauseWhenContested && present.size() >= 2)) {
            return;
        }
        for (String clan : present) {
            if (clan.equals(arena.dominantClan()) && !settings.ownerCanRecapture) {
                continue; // o dono atual não precisa (nem pode) recapturar
            }
            Long current = arena.progress().get(clan);
            if (current == null) {
                arena.progress().put(clan, previous.contains(clan) ? STEP_MILLIS : 0L);
            } else {
                arena.progress().put(clan, current + STEP_MILLIS);
            }
        }
    }

    private void checkCapture(Arena arena, ArenaSettings settings) {
        String winner = null;
        long best = -1;
        for (Map.Entry<String, Long> entry : arena.progress().entrySet()) {
            if (entry.getValue() >= settings.dominationMillis && entry.getValue() > best) {
                winner = entry.getKey();
                best = entry.getValue();
            }
        }
        if (winner != null) {
            capture(arena, winner);
        }
    }

    // ------------------------------------------------------------------ captura

    public void capture(Arena arena, String clan) {
        ArenaSettings settings = arena.settings();
        String previous = arena.dominantClan();
        long now = System.currentTimeMillis();

        arena.dominate(clan, now);
        if (settings.resetAllOnCapture) {
            arena.progress().clear();
        } else {
            arena.progress().remove(clan);
        }
        scores.addDomination(arena.name(), clan, settings.dominationPoints);
        repository.saveArenaState(arena.name(), clan, now);

        rewards.transfer(arena, previous, clan);
        rewards.runCaptureCommands(arena, clan);
        announce(arena, clan, settings);
        if (npcs != null) {
            npcs.refresh(arena);
            npcs.strikeChampion(arena);
        }
    }

    /** Remove o clan dominante (reset de arena/clan). */
    public void clearDominant(Arena arena) {
        String previous = arena.dominantClan();
        if (previous == null) {
            return;
        }
        arena.dominate(null, 0L);
        repository.saveArenaState(arena.name(), null, 0L);
        rewards.transfer(arena, previous, null);
        if (npcs != null) {
            npcs.refresh(arena);
        }
    }

    private void announce(Arena arena, String clan, ArenaSettings settings) {
        List<String> lines = messages.getList("dominated",
                "clan", clans.display(clan),
                "arena", arena.displayName(),
                "points", settings.dominationPoints);
        Iterable<? extends Player> recipients = config.settings().broadcastDomination
                ? Bukkit.getOnlinePlayers()
                : playersInside(arena);
        for (Player player : recipients) {
            for (String line : lines) {
                player.sendMessage(line);
            }
        }
        for (String line : lines) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
    }

    // ------------------------------------------------------------------ action bar

    /** Mensagem pronta de uma arena e quem deve recebê-la. */
    private static final class Bar {
        final Arena arena;
        final String text;
        final GlobalSettings.Recipients recipients;

        Bar(Arena arena, String text, GlobalSettings.Recipients recipients) {
            this.arena = arena;
            this.text = text;
            this.recipients = recipients;
        }

        boolean reaches(Player player) {
            switch (recipients) {
                case ALL:
                    return true;
                case WORLD:
                    return player.getWorld().getName().equals(arena.region().world());
                default:
                    return false;
            }
        }
    }

    /**
     * Disputas (clan tentando dominar / clans batalhando) vão para settings.actionbar-dispute-recipients
     * (padrão: todos online). "Dominante defendendo" vai para actionbar-holding-recipients (padrão: só quem está dentro).
     * Quem está dentro de uma arena sempre vê a mensagem daquela arena.
     */
    private void sendActionBars() {
        GlobalSettings settings = config.settings();
        List<Bar> bars = new ArrayList<Bar>();
        for (Arena arena : arenas.all()) {
            if (!arena.isActive() || arena.playersInside().isEmpty()) {
                continue;
            }
            String message = buildActionBar(arena);
            if (message != null && !message.isEmpty()) {
                bars.add(new Bar(arena, message,
                        isDispute(arena) ? settings.disputeRecipients : settings.holdingRecipients));
            }
        }
        if (bars.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            String text = messageFor(player, bars);
            if (text != null) {
                actionBar.send(player, text);
            }
        }
    }

    private static String messageFor(Player player, List<Bar> bars) {
        for (Bar bar : bars) {
            if (bar.arena.playersInside().contains(player.getUniqueId())) {
                return bar.text;
            }
        }
        for (Bar bar : bars) {
            if (bar.reaches(player)) {
                return bar.text;
            }
        }
        return null;
    }

    /** Há pelo menos um clan tentando dominar (e não apenas o dominante defendendo a área). */
    private boolean isDispute(Arena arena) {
        Set<String> present = arena.presence().keySet();
        if (present.isEmpty()) {
            return false;
        }
        return present.size() > 1 || !isHolding(arena, present.iterator().next());
    }

    /** Texto da ActionBar conforme a quantidade de clans na arena (tudo configurável no messages.yml). */
    public String buildActionBar(Arena arena) {
        List<String> present = sortedByProgress(arena);
        ArenaSettings settings = arena.settings();
        String max = Text.time(settings.dominationMillis);
        String area = arena.displayName();

        if (present.isEmpty()) {
            return arena.dominantClan() == null ? messages.get("actionbar.empty", "arena", area) : null;
        }
        if (present.size() == 1) {
            String clan = present.get(0);
            if (isHolding(arena, clan)) {
                return messages.get("actionbar.dominant-holding", "clan", clans.display(clan), "arena", area);
            }
            return messages.get("actionbar.single-clan",
                    "clan", clans.display(clan), "arena", area,
                    "time", Text.time(arena.progressOf(clan)), "max", max);
        }
        String suffix = settings.pauseWhenContested ? messages.get("actionbar.paused-suffix") : "";
        if (present.size() == 2) {
            String first = present.get(0);
            String second = present.get(1);
            return messages.get("actionbar.two-clans",
                    "clan1", clans.display(first), "clan2", clans.display(second),
                    "time1", timeLabel(arena, first), "time2", timeLabel(arena, second),
                    "max", max, "arena", area) + suffix;
        }
        return messages.get("actionbar.multiple-clans",
                "amount", present.size(),
                "clan1", clans.display(present.get(0)), "clan2", clans.display(present.get(1)),
                "time1", timeLabel(arena, present.get(0)),
                "others", present.size() - 2, "max", max, "arena", area) + suffix;
    }

    private String timeLabel(Arena arena, String clan) {
        return isHolding(arena, clan) ? messages.get("actionbar.owner-label") : Text.time(arena.progressOf(clan));
    }

    private boolean isHolding(Arena arena, String clan) {
        return clan.equals(arena.dominantClan()) && !arena.settings().ownerCanRecapture;
    }

    /** Clans presentes, do maior para o menor progresso. */
    public List<String> sortedByProgress(final Arena arena) {
        List<String> present = new ArrayList<String>(arena.presence().keySet());
        Collections.sort(present, (a, b) -> Long.compare(arena.progressOf(b), arena.progressOf(a)));
        return present;
    }

    private List<Player> playersInside(Arena arena) {
        List<Player> players = new ArrayList<Player>();
        for (UUID uuid : arena.playersInside()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }
}
