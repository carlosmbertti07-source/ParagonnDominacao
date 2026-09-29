package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import br.com.paragonn.dominacao.score.ClanStats;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/** /dominacao top [arena] */
public final class TopCommand extends SubCommand {

    public TopCommand(DominacaoPlugin plugin) {
        super(plugin, "top", "dominacao.top", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Arena arena = arena(sender, arg(args, 0));
        if (arena == null) {
            return;
        }
        List<ClanStats> top = plugin.scores().top(arena.name());
        plugin.messages().sendList(sender, "top.header", "arena", arena.displayName());
        if (top.isEmpty()) {
            plugin.messages().sendList(sender, "top.empty");
            return;
        }
        int size = Math.min(top.size(), plugin.configManager().settings().topSize);
        for (int i = 0; i < size; i++) {
            ClanStats stats = top.get(i);
            sender.sendMessage(plugin.messages().get("top.line",
                    "position", i + 1,
                    "clan", plugin.clans().display(stats.clan()),
                    "points", Text.number(stats.points()),
                    "dominations", stats.dominations()));
        }
        plugin.messages().sendList(sender, "top.footer", "arena", arena.displayName());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? arenaNames() : Collections.<String>emptyList();
    }
}
