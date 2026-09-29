package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/** /dominacao delete &lt;arena&gt; — remove a arena do config (os dados no banco são mantidos). */
public final class DeleteCommand extends SubCommand {

    public DeleteCommand(DominacaoPlugin plugin) {
        super(plugin, "delete", "dominacao.delete", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "/dominacao delete <arena>");
            return;
        }
        Arena arena = plugin.arenas().get(args[0]);
        if (arena == null) {
            send(sender, "unknown-arena", "arena", args[0]);
            return;
        }
        plugin.domination().clearDominant(arena);
        plugin.arenas().delete(arena.name());
        send(sender, "arena-deleted", "arena", arena.name());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? arenaNames() : Collections.<String>emptyList();
    }
}
