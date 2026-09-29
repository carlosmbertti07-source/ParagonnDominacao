package br.com.paragonn.dominacao;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.arena.ArenaManager;
import br.com.paragonn.dominacao.arena.SelectionManager;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.combat.CombatWallManager;
import br.com.paragonn.dominacao.command.DominacaoCommand;
import br.com.paragonn.dominacao.config.ConfigManager;
import br.com.paragonn.dominacao.config.MessageManager;
import br.com.paragonn.dominacao.database.DataRepository;
import br.com.paragonn.dominacao.database.DatabaseManager;
import br.com.paragonn.dominacao.domination.DominationManager;
import br.com.paragonn.dominacao.domination.TagDisplayManager;
import br.com.paragonn.dominacao.domination.TagRewardManager;
import br.com.paragonn.dominacao.hook.CitizensHook;
import br.com.paragonn.dominacao.hook.NoopNpcProvider;
import br.com.paragonn.dominacao.hook.NpcProvider;
import br.com.paragonn.dominacao.hook.PlaceholderHook;
import br.com.paragonn.dominacao.hook.SimpleClansHook;
import br.com.paragonn.dominacao.kill.KillManager;
import br.com.paragonn.dominacao.menu.TopMenu;
import br.com.paragonn.dominacao.npc.NpcManager;
import br.com.paragonn.dominacao.score.ScoreManager;
import br.com.paragonn.dominacao.util.ActionBar;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

/**
 * ParagonnDominacao — dominação de área por clans (SimpleClans) para 1.8.8.
 * Esta classe apenas monta os componentes e controla o ciclo de vida.
 */
public final class DominacaoPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messages;
    private DatabaseManager database;
    private DataRepository repository;
    private ClanManager clans;
    private ArenaManager arenas;
    private ScoreManager scores;
    private TagRewardManager rewards;
    private DominationManager domination;
    private NpcManager npcs;
    private KillManager kills;
    private SelectionManager selections;
    private CombatWallManager combatWall;
    private TopMenu topMenu;
    private TagDisplayManager tagDisplay;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        Plugin simpleClans = pluginManager.getPlugin("SimpleClans");
        if (!(simpleClans instanceof SimpleClans) || !simpleClans.isEnabled()) {
            getLogger().severe("SimpleClans nao encontrado! O plugin sera desativado.");
            pluginManager.disablePlugin(this);
            return;
        }

        configManager = new ConfigManager(this);
        configManager.load();
        messages = new MessageManager(this);
        messages.load();

        database = new DatabaseManager(configManager.database(), getDataFolder(), getLogger());
        repository = new DataRepository(database);
        scores = new ScoreManager(repository);
        try {
            database.connect();
            scores.loadAll();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Falha ao iniciar o banco de dados. O plugin sera desativado.", e);
            pluginManager.disablePlugin(this);
            return;
        }

        clans = new ClanManager(new SimpleClansHook((SimpleClans) simpleClans), configManager);
        arenas = new ArenaManager(configManager);
        arenas.reload();
        restoreArenaStates();

        selections = new SelectionManager();
        tagDisplay = new TagDisplayManager(this, arenas, clans);
        rewards = new TagRewardManager(arenas, clans, tagDisplay, getLogger());
        domination = new DominationManager(this, configManager, arenas, clans, scores, rewards, messages,
                repository, ActionBar.create(getLogger()));

        boolean citizens = pluginManager.getPlugin("Citizens") != null;
        NpcProvider provider = citizens ? new CitizensHook(getLogger()) : new NoopNpcProvider();
        topMenu = new TopMenu(this, scores, clans);
        topMenu.load();
        npcs = new NpcManager(this, provider, configManager, arenas, clans, scores, messages, repository, topMenu);
        npcs.load();
        domination.setNpcManager(npcs);
        kills = new KillManager(arenas, clans, scores, messages, npcs);

        combatWall = new CombatWallManager(this, configManager, arenas, messages);

        pluginManager.registerEvents(kills, this);
        pluginManager.registerEvents(npcs, this);
        pluginManager.registerEvents(combatWall, this);
        pluginManager.registerEvents(topMenu, this);
        pluginManager.registerEvents(tagDisplay, this);

        DominacaoCommand command = new DominacaoCommand(this);
        PluginCommand pluginCommand = getCommand("dominacao");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            PlaceholderHook.registerHook(this);
            getLogger().info("PlaceholderAPI detectado: placeholders %dominacao_*% registrados.");
        }
        if (!citizens) {
            getLogger().warning("Citizens nao encontrado: NPCs de top/campeao desativados.");
        }

        startTasks();
        getLogger().info("ParagonnDominacao ativado com " + arenas.all().size() + " arena(s).");
    }

    @Override
    public void onDisable() {
        if (combatWall != null) {
            combatWall.shutdown();
        }
        if (tagDisplay != null) {
            tagDisplay.shutdown();
        }
        if (npcs != null) {
            npcs.shutdown();
        }
        if (domination != null) {
            domination.stop();
        }
        if (scores != null) {
            try {
                scores.flush(false);
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "Erro ao salvar dados no desligamento", e);
            }
        }
        if (database != null) {
            database.close();
        }
    }

    /** /dominacao reload: relê arquivos e reinicia tarefas, sem perder progresso em andamento. */
    public void reloadAll() {
        configManager.load();
        messages.load();
        topMenu.load();
        arenas.reload();
        startTasks();
    }

    private void startTasks() {
        domination.start();
        npcs.start();
        combatWall.start();
        tagDisplay.start();
        if (saveTask != null) {
            saveTask.cancel();
        }
        long interval = configManager.database().saveIntervalSeconds * 20L;
        saveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            scores.flush(true);
            kills.cleanupCooldowns(maxKillCooldown());
        }, interval, interval);
    }

    private long maxKillCooldown() {
        long max = 0;
        for (Arena arena : arenas.all()) {
            max = Math.max(max, arena.settings().killCooldownMillis);
        }
        return max;
    }

    private void restoreArenaStates() {
        try {
            for (Map.Entry<String, DataRepository.ArenaState> entry : repository.loadArenaStates().entrySet()) {
                Arena arena = arenas.get(entry.getKey());
                if (arena != null && entry.getValue().dominantClan != null) {
                    arena.dominate(entry.getValue().dominantClan, entry.getValue().dominatedAt);
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Erro ao carregar o estado das arenas", e);
        }
    }

    // ------------------------------------------------------------------ acesso aos componentes

    public ConfigManager configManager() {
        return configManager;
    }

    public MessageManager messages() {
        return messages;
    }

    public ClanManager clans() {
        return clans;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public ScoreManager scores() {
        return scores;
    }

    public TagRewardManager rewards() {
        return rewards;
    }

    public DominationManager domination() {
        return domination;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public SelectionManager selections() {
        return selections;
    }
}
