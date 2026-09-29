package br.com.paragonn.dominacao.npc;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.arena.ArenaManager;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.config.ConfigManager;
import br.com.paragonn.dominacao.config.MessageManager;
import br.com.paragonn.dominacao.database.DataRepository;
import br.com.paragonn.dominacao.hook.NpcProvider;
import br.com.paragonn.dominacao.menu.TopMenu;
import br.com.paragonn.dominacao.score.ClanStats;
import br.com.paragonn.dominacao.score.PlayerStats;
import br.com.paragonn.dominacao.score.ScoreManager;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * NPC #1 (top de clans) e NPC #2 (campeão do clan dominante), com hologramas.
 * Atualiza no intervalo configurado por arena e imediatamente em capturas/abates relevantes.
 */
public final class NpcManager implements Listener {

    public enum Type {
        TOP("top"), CHAMPION("campeao");

        public final String id;

        Type(String id) {
            this.id = id;
        }
    }

    private final Plugin plugin;
    private final NpcProvider provider;
    private final ConfigManager config;
    private final ArenaManager arenas;
    private final ClanManager clans;
    private final ScoreManager scores;
    private final MessageManager messages;
    private final DataRepository repository;
    private final TopMenu topMenu;

    private final Map<String, Integer> npcIds = new HashMap<String, Integer>();
    private final Map<String, Hologram> holograms = new HashMap<String, Hologram>();
    /** arena -> nome do jogador cuja skin está aplicada no NPC campeão. */
    private final Map<String, String> championSkins = new HashMap<String, String>();
    /** arena -> skin aplicada no NPC de top. */
    private final Map<String, String> topSkins = new HashMap<String, String>();
    private final Map<String, Long> lastRefresh = new HashMap<String, Long>();
    /** Arenas com abates/mortes novos: atualizadas juntas no próximo tick (várias mortes = 1 atualização). */
    private final Set<String> dirty = new HashSet<String>();
    private BukkitTask task;
    private BukkitTask pendingTask;

    public NpcManager(Plugin plugin, NpcProvider provider, ConfigManager config, ArenaManager arenas,
                      ClanManager clans, ScoreManager scores, MessageManager messages, DataRepository repository,
                      TopMenu topMenu) {
        this.topMenu = topMenu;
        this.plugin = plugin;
        this.provider = provider;
        this.config = config;
        this.arenas = arenas;
        this.clans = clans;
        this.scores = scores;
        this.messages = messages;
        this.repository = repository;
    }

    public void load() {
        try {
            npcIds.clear();
            npcIds.putAll(repository.loadNpcs());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao carregar NPCs", e);
        }
        provider.registerClickHandler(plugin, this::handleClick);
    }

    /** Clique no NPC de top abre o menu de rankings daquela arena. */
    private void handleClick(Player player, int npcId) {
        for (Map.Entry<String, Integer> entry : npcIds.entrySet()) {
            if (entry.getValue() != npcId || !entry.getKey().endsWith(":" + Type.TOP.id)) {
                continue;
            }
            Arena arena = arenas.get(entry.getKey().substring(0, entry.getKey().indexOf(':')));
            if (arena == null) {
                return;
            }
            if (!player.hasPermission("dominacao.top")) {
                messages.send(player, "no-permission");
                return;
            }
            topMenu.open(player, arena);
            return;
        }
    }

    public boolean isAvailable() {
        return provider.available();
    }

    /** Verifica a cada segundo quais arenas já passaram do intervalo de atualização. */
    public void start() {
        stop();
        lastRefresh.clear();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Arena arena : arenas.all()) {
                Long last = lastRefresh.get(arena.name());
                if (last == null || now - last >= arena.settings().npcUpdateSeconds * 1000L) {
                    refresh(arena);
                }
            }
        }, 100L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
        dirty.clear();
    }

    /** Pede atualização em tempo real (abate/morte): acontece no próximo tick, agrupada por arena. */
    public void markDirty(Arena arena) {
        dirty.add(arena.name());
        if (pendingTask != null) {
            return;
        }
        pendingTask = Bukkit.getScheduler().runTask(plugin, () -> {
            pendingTask = null;
            List<String> names = new ArrayList<String>(dirty);
            dirty.clear();
            for (String name : names) {
                Arena current = arenas.get(name);
                if (current != null) {
                    refresh(current);
                }
            }
        });
    }

    public void shutdown() {
        stop();
        for (Hologram hologram : holograms.values()) {
            hologram.remove();
        }
        holograms.clear();
    }

    /** Cria o NPC (ou move o existente) na localização informada. */
    public void place(Type type, Arena arena, Location location) {
        String key = key(arena, type);
        Integer id = npcIds.get(key);
        if (id != null && provider.exists(id)) {
            provider.moveTo(id, location);
        } else {
            String name = type == Type.TOP ? arena.settings().topNpcName : arena.settings().championDefaultName;
            id = provider.create(name, location);
            npcIds.put(key, id);
            repository.saveNpc(arena.name(), type.id, id);
            (type == Type.TOP ? topSkins : championSkins).remove(arena.name());
        }
        Hologram hologram = holograms.remove(key);
        if (hologram != null) {
            hologram.remove();
        }
        refresh(arena);
    }

    public void refresh(Arena arena) {
        lastRefresh.put(arena.name(), System.currentTimeMillis());
        refreshBoard(arena); // holograma avulso não depende do Citizens
        if (!provider.available()) {
            return;
        }
        refreshTop(arena);
        refreshChampion(arena);
    }

    // ------------------------------------------------------------------ holograma avulso de top

    /** /dominacao holograma set: salva a posição no config.yml (arenas.<nome>.top-hologram). */
    public void placeBoard(Arena arena, Location location) {
        Map<String, Object> position = new LinkedHashMap<String, Object>();
        position.put("world", location.getWorld().getName());
        position.put("x", round(location.getX()));
        position.put("y", round(location.getY()));
        position.put("z", round(location.getZ()));
        config.setArenaValue(arena.name(), "top-hologram", position);
        arenas.reload();
        removeHologram(boardKey(arena));
        refreshBoard(arenas.get(arena.name()));
    }

    public void removeBoard(Arena arena) {
        config.setArenaValue(arena.name(), "top-hologram", null);
        arenas.reload();
        removeHologram(boardKey(arena));
    }

    /** Título + descrição + top N de clans por pontos (hologram.board no messages.yml). */
    public void refreshBoard(Arena arena) {
        if (arena == null) {
            return;
        }
        String worldName = arena.settings().boardWorld;
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            removeHologram(boardKey(arena));
            return;
        }
        List<String> lines = new ArrayList<String>(messages.getList("hologram.board.header", placeholders(arena)));
        List<ClanStats> top = scores.top(arena.name());
        int size = Math.min(config.settings().topSize, 10);
        for (int i = 0; i < size; i++) {
            if (i < top.size()) {
                ClanStats stats = top.get(i);
                lines.add(messages.get("hologram.board.line",
                        "position", i + 1, "clan", clans.display(stats.clan()),
                        "points", Text.number(stats.points()), "dominations", stats.dominations()));
            } else {
                lines.add(messages.get("hologram.board.empty-line", "position", i + 1));
            }
        }
        lines.addAll(messages.getList("hologram.board.footer", placeholders(arena)));

        double spacing = config.settings().hologramLineSpacing;
        Location anchor = new Location(world, arena.settings().boardX, arena.settings().boardY, arena.settings().boardZ)
                .add(0, 1.0 + (lines.size() - 1) * spacing, 0);
        hologram(boardKey(arena)).update(anchor, lines, spacing);
    }

    private Object[] placeholders(Arena arena) {
        return new Object[]{"arena", arena.displayName(),
                "arena_upper", arena.displayName().toUpperCase(new Locale("pt", "BR"))};
    }

    private void removeHologram(String key) {
        Hologram hologram = holograms.remove(key);
        if (hologram != null) {
            hologram.remove();
        }
    }

    private static String boardKey(Arena arena) {
        return arena.name() + ":board";
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Raio visual (sem dano e sem fogo) no NPC campeão. Chamado na captura, depois de o NPC já ter
     * trocado de skin para o campeão do novo clan. Vários raios saem com intervalo de 8 ticks.
     */
    public void strikeChampion(Arena arena) {
        if (!provider.available() || !arena.settings().captureLightning) {
            return;
        }
        final Integer id = npcIds.get(key(arena, Type.CHAMPION));
        if (id == null) {
            return;
        }
        for (int i = 0; i < arena.settings().captureLightningStrikes; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location location = provider.location(id);
                if (location != null && location.getWorld() != null) {
                    location.getWorld().strikeLightningEffect(location);
                }
            }, 5L + i * 8L);
        }
    }

    public void refreshTop(Arena arena) {
        Integer id = npcIds.get(key(arena, Type.TOP));
        Location base = npcLocation(arena, Type.TOP);
        if (id == null || base == null) {
            return;
        }
        // Skin configurável (npcs.top-skin): só reaplica quando o valor muda.
        String skin = arena.settings().topNpcSkin;
        if (!skin.isEmpty() && !skin.equals(topSkins.get(arena.name()))) {
            provider.setSkin(id, skin, arena.settings().topNpcName);
            topSkins.put(arena.name(), skin);
            base = provider.location(id);
            if (base == null) {
                return;
            }
        }
        // Acima do NPC ficam só as linhas de título; o ranking fica no menu e no holograma avulso.
        List<String> lines = messages.getList("hologram.top.lines", placeholders(arena));
        hologram(arena, Type.TOP).update(top(base, arena, lines.size()), lines, config.settings().hologramLineSpacing);
    }

    public void refreshChampion(Arena arena) {
        if (!provider.available()) {
            return;
        }
        String key = key(arena, Type.CHAMPION);
        Integer id = npcIds.get(key);
        Location base = npcLocation(arena, Type.CHAMPION);
        if (id == null || base == null) {
            return;
        }
        String dominant = arena.dominantClan();
        PlayerStats best = dominant == null ? null : scores.bestPlayer(arena.name(), clans.memberIds(dominant));

        List<String> lines;
        String skin;
        if (dominant == null) {
            lines = messages.getList("hologram.champion.none", "arena", arena.displayName());
            skin = arena.settings().championDefaultName;
        } else if (best == null) {
            lines = messages.getList("hologram.champion.no-kills",
                    "arena", arena.displayName(), "clan", clans.display(dominant));
            skin = arena.settings().championDefaultName;
        } else {
            lines = messages.getList("hologram.champion.lines",
                    "arena", arena.displayName(), "clan", clans.display(dominant),
                    "player", best.name(), "kills", Text.number(best.kills()),
                    "deaths", Text.number(best.deaths()), "kdr", Text.decimal(best.kdr()));
            skin = best.name();
        }

        if (!skin.equals(championSkins.get(arena.name()))) {
            provider.setSkin(id, skin, arena.settings().championDefaultName);
            championSkins.put(arena.name(), skin);
            base = provider.location(id);
            if (base == null) {
                return;
            }
        }
        hologram(arena, Type.CHAMPION).update(top(base, arena, lines.size()), lines, config.settings().hologramLineSpacing);
    }

    /** Hologramas não podem ser salvos junto com o chunk: removemos e recriamos no próximo refresh. */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Hologram hologram : holograms.values()) {
            if (hologram.isIn(event.getChunk())) {
                hologram.remove();
            }
        }
    }

    private Location npcLocation(Arena arena, Type type) {
        Integer id = npcIds.get(key(arena, type));
        return id == null ? null : provider.location(id);
    }

    /** Linha de cima do holograma: acima da cabeça do NPC, subindo conforme o número de linhas. */
    private Location top(Location base, Arena arena, int lineCount) {
        double height = arena.settings().hologramHeight + (lineCount - 1) * config.settings().hologramLineSpacing;
        Location location = base.clone().add(0, height, 0);
        location.setYaw(0);
        location.setPitch(0);
        return location;
    }

    private Hologram hologram(Arena arena, Type type) {
        return hologram(key(arena, type));
    }

    private Hologram hologram(String key) {
        Hologram hologram = holograms.get(key);
        if (hologram == null) {
            hologram = new Hologram();
            holograms.put(key, hologram);
        }
        return hologram;
    }

    private static String key(Arena arena, Type type) {
        return arena.name() + ":" + type.id;
    }
}
