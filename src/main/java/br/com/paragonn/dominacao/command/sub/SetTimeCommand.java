package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /dominacao settime &lt;arena&gt; &lt;minutos&gt; */
public final class SetTimeCommand extends SubCommand {

    public SetTimeCommand(DominacaoPlugin plugin) {
        super(plugin, "settime", "dominacao.admin", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dominacao settime <arena> <minutos>");
            return;
        }
        Arena arena = plugin.arenas().get(args[0]);
        if (arena == null) {
            send(sender, "unknown-arena", "arena", args[0]);
            return;
        }
        Long minutes = number(sender, args[1], 1);
        if (minutes == null) {
            return;
        }
        plugin.configManager().setArenaValue(arena.name(), "domination-time", minutes * 60);
        plugin.arenas().reload();
        send(sender, "settime-done", "arena", arena.name(), "minutes", minutes);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return arenaNames();
        }
        return args.length == 2 ? Arrays.asList("5", "10", "15", "20") : Collections.<String>emptyList();
    }
}
