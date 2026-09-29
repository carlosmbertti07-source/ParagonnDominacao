package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import br.com.paragonn.dominacao.score.ClanStats;
import br.com.paragonn.dominacao.score.PlayerStats;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /dominacao stats [arena] */
public final class StatsCommand extends SubCommand {

    public StatsCommand(DominacaoPlugin plugin) {
        super(plugin, "stats", "dominacao.stats", true);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Arena arena = arena(sender, arg(args, 0));
        if (arena == null) {
            return;
        }
        Player player = (Player) sender;
        String clan = plugin.clans().clanKey(player);
        PlayerStats stats = plugin.scores().player(arena.name(), player.getUniqueId());
        ClanStats clanStats = clan == null ? null : plugin.scores().clan(arena.name(), clan);

        int kills = stats == null ? 0 : stats.kills();
        int deaths = stats == null ? 0 : stats.deaths();
        plugin.messages().sendList(sender, "stats",
                "arena", arena.displayName(),
                "clan", clan == null ? plugin.messages().get("placeholder.no-clan") : plugin.clans().display(clan),
                "kills", Text.number(kills),
                "deaths", Text.number(deaths),
                "kdr", Text.decimal(stats == null ? 0 : stats.kdr()),
                "time", Text.duration(stats == null ? 0 : stats.timeMillis()),
                "points", Text.number(clanStats == null ? 0 : clanStats.points()),
                "dominations", clanStats == null ? 0 : clanStats.dominations(),
                "position", clan == null ? "-" : String.valueOf(plugin.scores().position(arena.name(), clan)));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? arenaNames() : Collections.<String>emptyList();
    }
}
