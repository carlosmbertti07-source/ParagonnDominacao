package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.command.SubCommand;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /dominacao points &lt;add|remove|set&gt; &lt;clan&gt; &lt;quantidade&gt; [arena] */
public final class PointsCommand extends SubCommand {

    private static final String USAGE = "/dominacao points <add|remove|set> <clan> <quantidade> [arena]";

    public PointsCommand(DominacaoPlugin plugin) {
        super(plugin, "points", "dominacao.points", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, USAGE);
            return;
        }
        String action = lower(args[0]);
        if (!Arrays.asList("add", "remove", "set").contains(action)) {
            usage(sender, USAGE);
            return;
        }
        String clan = ClanManager.normalize(args[1]);
        if (!plugin.clans().exists(clan)) {
            send(sender, "clan-not-found", "clan", args[1]);
            return;
        }
        Long amount = number(sender, args[2], 0);
        Arena arena = amount == null ? null : arena(sender, arg(args, 3));
        if (arena == null) {
            return;
        }
        if ("add".equals(action)) {
            plugin.scores().addPoints(arena.name(), clan, amount);
        } else if ("remove".equals(action)) {
            plugin.scores().addPoints(arena.name(), clan, -amount);
        } else {
            plugin.scores().setPoints(arena.name(), clan, amount);
        }
        long total = plugin.scores().clan(arena.name(), clan).points();
        plugin.npcs().refreshBoard(arena);
        send(sender, "points-done", "clan", plugin.clans().display(clan), "points", Text.number(total),
                "arena", arena.name());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        switch (args.length) {
            case 1:
                return Arrays.asList("add", "remove", "set");
            case 3:
                return Arrays.asList("10", "50", "100");
            case 4:
                return arenaNames();
            default:
                return Collections.emptyList();
        }
    }
}
