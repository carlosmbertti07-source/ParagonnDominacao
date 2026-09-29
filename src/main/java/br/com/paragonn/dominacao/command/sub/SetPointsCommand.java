package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /dominacao setpoints &lt;kill|capture&gt; &lt;quantidade&gt; [arena] */
public final class SetPointsCommand extends SubCommand {

    public SetPointsCommand(DominacaoPlugin plugin) {
        super(plugin, "setpoints", "dominacao.admin", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dominacao setpoints <kill|capture> <quantidade> [arena]");
            return;
        }
        String type = lower(args[0]);
        String path;
        if ("kill".equals(type)) {
            path = "points.kill";
        } else if ("capture".equals(type)) {
            path = "points.domination";
        } else {
            usage(sender, "/dominacao setpoints <kill|capture> <quantidade> [arena]");
            return;
        }
        Long amount = number(sender, args[1], 0);
        Arena arena = amount == null ? null : arena(sender, arg(args, 2));
        if (arena == null) {
            return;
        }
        plugin.configManager().setArenaValue(arena.name(), path, amount.intValue());
        plugin.arenas().reload();
        send(sender, "setpoints-done", "type", type, "amount", amount, "arena", arena.name());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        switch (args.length) {
            case 1:
                return Arrays.asList("kill", "capture");
            case 3:
                return arenaNames();
            default:
                return Collections.emptyList();
        }
    }
}
