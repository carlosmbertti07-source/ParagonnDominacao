package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import br.com.paragonn.dominacao.util.Text;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/** /dominacao status [arena] — clan dominante e progresso de cada clan na arena. */
public final class StatusCommand extends SubCommand {

    public StatusCommand(DominacaoPlugin plugin) {
        super(plugin, "status", "dominacao.use", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Arena arena = arena(sender, arg(args, 0));
        if (arena == null) {
            return;
        }
        String dominant = arena.dominantClan();
        plugin.messages().sendList(sender, "status.header",
                "arena", arena.displayName(),
                "dominant", dominant == null ? plugin.messages().get("placeholder.none") : plugin.clans().display(dominant),
                "max", Text.time(arena.settings().dominationMillis),
                "state", plugin.messages().get(arena.isActive() ? "status.active" : "status.inactive"));

        List<String> present = plugin.domination().sortedByProgress(arena);
        if (present.isEmpty()) {
            plugin.messages().sendList(sender, "status.no-clans");
            return;
        }
        for (String clan : present) {
            boolean holding = clan.equals(dominant) && !arena.settings().ownerCanRecapture;
            sender.sendMessage(plugin.messages().get("status.clan-line",
                    "clan", plugin.clans().display(clan),
                    "players", arena.presence().get(clan).size(),
                    "time", holding ? plugin.messages().get("actionbar.owner-label") : Text.time(arena.progressOf(clan)),
                    "max", Text.time(arena.settings().dominationMillis)));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? arenaNames() : Collections.<String>emptyList();
    }
}
