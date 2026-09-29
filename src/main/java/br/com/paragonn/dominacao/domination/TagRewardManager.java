package br.com.paragonn.dominacao.domination;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.arena.ArenaManager;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.config.ArenaSettings;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tag do clan dominante e recompensas.
 * - A tag é exposta pelo placeholder %dominacao_tag% (recomendado: não precisa de sincronização).
 * - Opcionalmente executa comandos por membro ao ganhar/perder a tag (ex.: LuckPerms),
 *   e comandos únicos na captura. Tudo configurável por arena no config.yml.
 */
public final class TagRewardManager {

    private final ArenaManager arenas;
    private final ClanManager clans;
    private final Logger logger;
    private final TagDisplayManager display;

    public TagRewardManager(ArenaManager arenas, ClanManager clans, TagDisplayManager display, Logger logger) {
        this.arenas = arenas;
        this.clans = clans;
        this.display = display;
        this.logger = logger;
    }

    /** Transfere a tag do antigo dominante para o novo (qualquer um pode ser null). */
    public void transfer(Arena arena, String oldClan, String newClan) {
        if (display != null) {
            display.refreshAll(); // tag no chat/acima da cabeça muda na hora
        }
        ArenaSettings settings = arena.settings();
        if (!settings.tagEnabled) {
            return;
        }
        if (oldClan != null && !oldClan.equals(newClan)) {
            runForMembers(settings.loseCommands, arena, oldClan);
        }
        if (newClan != null && !newClan.equals(oldClan)) {
            runForMembers(settings.gainCommands, arena, newClan);
        }
    }

    public void runCaptureCommands(Arena arena, String clan) {
        for (String command : arena.settings().captureCommands) {
            dispatch(Text.replace(command,
                    "clan", clan,
                    "clan_display", clans.display(clan),
                    "arena", arena.name(),
                    "points", arena.settings().dominationPoints));
        }
    }

    /** Tag a exibir para o jogador (vazio se o clan dele não domina nenhuma arena com tag habilitada). */
    public String tagFor(Player player) {
        String clan = clans.clanKey(player);
        if (clan == null) {
            return "";
        }
        for (Arena arena : arenas.all()) {
            if (arena.settings().tagEnabled && clan.equals(arena.dominantClan())) {
                return Text.color(arena.settings().tag);
            }
        }
        return "";
    }

    private void runForMembers(List<String> commands, Arena arena, String clan) {
        if (commands.isEmpty()) {
            return;
        }
        String tag = arena.settings().tag;
        for (ClanManager.Member member : clans.members(clan)) {
            for (String command : commands) {
                dispatch(Text.replace(command,
                        "player", member.name,
                        "uuid", member.uuid,
                        "clan", clan,
                        "tag", tag,
                        "arena", arena.name()));
            }
        }
    }

    private void dispatch(String command) {
        String clean = command.startsWith("/") ? command.substring(1) : command;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), clean);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Falha ao executar comando de recompensa: " + clean, e);
        }
    }
}
