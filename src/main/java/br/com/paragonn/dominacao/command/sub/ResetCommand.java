package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /dominacao reset clan &lt;clan&gt; [arena] — zera pontos/estatísticas do clan na arena.
 * /dominacao reset arena &lt;arena&gt;       — zera tudo da arena (pontos, stats, dominante, progresso).
 */
public final class ResetCommand extends SubCommand {

    public ResetCommand(DominacaoPlugin plugin) {
        super(plugin, "reset", "dominacao.admin", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dominacao reset <clan|arena> <nome> [arena]");
            return;
        }
        String type = lower(args[0]);
        if ("clan".equals(type)) {
            resetClan(sender, ClanManager.normalize(args[1]), arg(args, 2));
        } else if ("arena".equals(type)) {
            resetArena(sender, args[1]);
        } else {
            usage(sender, "/dominacao reset <clan|arena> <nome> [arena]");
        }
    }

    private void resetClan(CommandSender sender, String clan, String arenaName) {
        Arena arena = arena(sender, arenaName);
        if (arena == null) {
            return;
        }
        plugin.scores().resetClan(arena.name(), clan);
        arena.progress().remove(clan);
        if (clan.equals(arena.dominantClan())) {
            plugin.domination().clearDominant(arena);
        }
        plugin.npcs().refresh(arena);
        send(sender, "reset-clan-done", "clan", clan, "arena", arena.name());
    }

    private void resetArena(CommandSender sender, String arenaName) {
        Arena arena = plugin.arenas().get(arenaName);
        if (arena == null) {
            send(sender, "unknown-arena", "arena", arenaName);
            return;
        }
        plugin.scores().resetArena(arena.name());
        arena.progress().clear();
        plugin.domination().clearDominant(arena);
        plugin.npcs().refresh(arena);
        send(sender, "reset-arena-done", "arena", arena.name());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("clan", "arena");
        }
        if (args.length == 2 && "arena".equalsIgnoreCase(args[0])) {
            return arenaNames();
        }
        if (args.length == 3 && "clan".equalsIgnoreCase(args[0])) {
            return arenaNames();
        }
        return Collections.emptyList();
    }
}
